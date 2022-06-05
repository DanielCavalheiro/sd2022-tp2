package tp2.impl.servers.common;

import static tp2.api.service.java.Result.error;
import static tp2.api.service.java.Result.ok;
import static tp2.api.service.java.Result.redirect;
import static tp2.api.service.java.Result.ErrorCode.BAD_REQUEST;
import static tp2.api.service.java.Result.ErrorCode.FORBIDDEN;
import static tp2.api.service.java.Result.ErrorCode.NOT_FOUND;
import static tp2.impl.clients.Clients.FilesClients;
import static tp2.impl.clients.Clients.UsersClients;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.glassfish.jersey.server.ResourceConfig;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.Gson;

import java.util.UUID;

import tp2.api.FileInfo;
import tp2.api.User;
import tp2.api.service.java.Directory;
import tp2.api.service.java.Result;
import tp2.api.service.java.Result.ErrorCode;
import tp2.impl.kafka.KafkaPublisher;
import tp2.impl.kafka.KafkaSubscriber;
import tp2.impl.kafka.RecordProcessor;
import tp2.impl.kafka.sync.SyncPoint;
import tp2.impl.servers.rest.DirectoryRestServer;
import util.Hash;
import util.Token;

public class JavaDirectory implements Directory, RecordProcessor {

	static final long USER_CACHE_EXPIRATION = 3000;

	final LoadingCache<UserInfo, Result<User>> users = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMillis(USER_CACHE_EXPIRATION))
			.build(new CacheLoader<>() {
				@Override
				public Result<User> load(UserInfo info) throws Exception {
					var res = UsersClients.get().getUser(info.userId(), info.password());
					if (res.error() == ErrorCode.TIMEOUT)
						return error(BAD_REQUEST);
					else
						return res;
				}
			});

	final static Logger Log = Logger.getLogger(JavaDirectory.class.getName());
	final ExecutorService executor = Executors.newCachedThreadPool();

	final Map<String, ExtendedFileInfo> files = new ConcurrentHashMap<>();
	final Map<String, UserFiles> userFiles = new ConcurrentHashMap<>();
	final Map<URI, FileCounts> fileCounts = new ConcurrentHashMap<>();
	ObjectMapper mapper = new ObjectMapper();

	static final String KAFKA_BROKERS = "kafka:9092";
	static final String FROM_BEGINNING = "earliest";

	public enum Operations {
		WRITE_FILE,
		DELETE_FILE,
		SHARE_FILE,
		UNSHARE_FILE,
		GET_FILE,
		LS_FILE,
		DELETE_USER_FILES;

		public static List<String> toList() {
			return List.of(Operations.values())
					.stream()
					.map(operation -> operation.name())
					.collect(Collectors.toList());
		}

		static Operations findByName(String name) {
			for (Operations operation : Operations.values()) {
				if (operation.name().equals(name)) {
					return operation;
				}
			}
			return null;
		}
	}

	final String replicaId;
	final KafkaPublisher sender;
	final KafkaSubscriber receiver;
	final SyncPoint<String> sync;

	public JavaDirectory() {
		replicaId = UUID.randomUUID().toString();
		sender = KafkaPublisher.createPublisher(KAFKA_BROKERS);
		receiver = KafkaSubscriber.createSubscriber(KAFKA_BROKERS, Operations.toList(), FROM_BEGINNING);
		receiver.start(false, (r) -> {
			onReceive(r);
		});
		sync = new SyncPoint<>();
		System.out.println(replicaId);
	}

	@Override
	public Result<FileInfo> writeFile(String filename, byte[] data, String userId, String password) {
		if (badParam(filename) || badParam(userId))
			return error(BAD_REQUEST);

		var user = getUser(userId, password);
		if (!user.isOK())
			return error(user.error());

		UserFiles uf = userFiles.computeIfAbsent(userId, (k) -> new UserFiles());
		synchronized (uf) {
			var fileId = fileId(filename, userId);
			Token.set(Hash.of(fileId, "mysecret", System.nanoTime()));
			var file = files.get(fileId);
			var info = file != null ? file.info() : new FileInfo();
			ArrayList<URI> uris = new ArrayList<>();
			int serverSucc = 0;
			Queue<URI> candidateServers = candidateFileServers(file);
			for (var uri : candidateServers) {
				var result = FilesClients.get(uri).writeFile(fileId, data, Token.get());
				if (result.isOK()) {
					serverSucc++;
					uris.add(uri);
				} else
					Log.info(String.format("Files.writeFile(...) to %s failed with: %s \n", uri, result));

				if (serverSucc == 2) {
					info.setOwner(userId);
					info.setFilename(filename);
					info.setFileURL(String.format("%s/files/%s", uri, fileId));
					uf.owned.add(fileId);
				}

			}
			files.put(fileId, file = new ExtendedFileInfo(uris, fileId, info));

			if (serverSucc > 1) {
				sentToKafkaTopic(Operations.WRITE_FILE, file);
				return ok(file.info());
			}

			return error(BAD_REQUEST);
		}
	}

	@Override
	public Result<Void> deleteFile(String filename, String userId, String password) {
		if (badParam(filename) || badParam(userId))
			return error(BAD_REQUEST);

		var fileId = fileId(filename, userId);

		var file = files.get(fileId);
		if (file == null)
			return error(NOT_FOUND);

		var user = getUser(userId, password);
		if (!user.isOK())
			return error(user.error());

		var uf = userFiles.getOrDefault(userId, new UserFiles());
		synchronized (uf) {
			var info = files.remove(fileId);
			uf.owned().remove(fileId);

			executor.execute(() -> {
				this.removeSharesOfFile(info);
				file.uris.forEach(
						u -> FilesClients.get(u).deleteFile(fileId, password));

			});

			sentToKafkaTopic(Operations.DELETE_FILE, file);

			// getFileCounts(info.uri(), false).numFiles().decrementAndGet();
		}
		return ok();
	}

	@Override
	public Result<Void> shareFile(String filename, String userId, String userIdShare, String password) {
		if (badParam(filename) || badParam(userId) || badParam(userIdShare))
			return error(BAD_REQUEST);

		var fileId = fileId(filename, userId);

		var file = files.get(fileId);
		if (file == null || getUser(userIdShare, "").error() == NOT_FOUND)
			return error(NOT_FOUND);

		var user = getUser(userId, password);
		if (!user.isOK())
			return error(user.error());

		var uf = userFiles.computeIfAbsent(userIdShare, (k) -> new UserFiles());
		synchronized (uf) {
			uf.shared().add(fileId);
			file.info().getSharedWith().add(userIdShare);
		}

		sentToKafkaTopic(Operations.SHARE_FILE, new SharingInfo(fileId, userIdShare));

		return ok();
	}

	@Override
	public Result<Void> unshareFile(String filename, String userId, String userIdShare, String password) {
		if (badParam(filename) || badParam(userId) || badParam(userIdShare))
			return error(BAD_REQUEST);

		var fileId = fileId(filename, userId);

		var file = files.get(fileId);
		if (file == null || getUser(userIdShare, "").error() == NOT_FOUND)
			return error(NOT_FOUND);

		var user = getUser(userId, password);
		if (!user.isOK())
			return error(user.error());

		var uf = userFiles.computeIfAbsent(userIdShare, (k) -> new UserFiles());
		synchronized (uf) {
			uf.shared().remove(fileId);
			file.info().getSharedWith().remove(userIdShare);
		}

		sentToKafkaTopic(Operations.UNSHARE_FILE, new SharingInfo(fileId, userIdShare));

		return ok();
	}

	@Override
	public Result<byte[]> getFile(String filename, String userId, String accUserId, String password) {
		if (badParam(filename))
			return error(BAD_REQUEST);

		var fileId = fileId(filename, userId);
		var file = files.get(fileId);
		if (file == null)
			return error(NOT_FOUND);

		var user = getUser(accUserId, password);
		if (!user.isOK())
			return error(user.error());

		if (!file.info().hasAccess(accUserId))
			return error(FORBIDDEN);

		List<URI> uris = files.get(fileId).uris();
		List<URI> validUris = new LinkedList<>();
		for (URI validURi : FilesClients.all()) {
			if (uris.contains(validURi)) {
				validUris.add(validURi);
			}
		}

		String uriString = "";
		for (URI uri : validUris) {
			String url = String.format("%s/files/%s", uri, fileId);
			uriString += url + "###";
		}

		uriString.substring(0, uriString.length() - 3);

		return redirect(uriString);
	}

	@Override
	public Result<List<FileInfo>> lsFile(String userId, String password) {
		if (badParam(userId))
			return error(BAD_REQUEST);

		var user = getUser(userId, password);
		if (!user.isOK())
			return error(user.error());

		var uf = userFiles.getOrDefault(userId, new UserFiles());
		synchronized (uf) {
			var infos = Stream.concat(uf.owned().stream(), uf.shared().stream()).map(f -> files.get(f).info())
					.collect(Collectors.toSet());

			return ok(new ArrayList<>(infos));
		}
	}

	public static String fileId(String filename, String userId) {
		return userId + JavaFiles.DELIMITER + filename;
	}

	private static boolean badParam(String str) {
		return str == null || str.length() == 0;
	}

	private Result<User> getUser(String userId, String password) {
		try {
			return users.get(new UserInfo(userId, password));
		} catch (Exception x) {
			x.printStackTrace();
			return error(ErrorCode.INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> deleteUserFiles(String userId, String password, String token) {
		UserInfo userInfo = new UserInfo(userId, password);
		users.invalidate(userInfo);

		var fileIds = userFiles.remove(userId);
		if (fileIds != null)
			for (var id : fileIds.owned()) {
				var file = files.remove(id);
				removeSharesOfFile(file);
				// getFileCounts(file.uri(), false).numFiles().decrementAndGet();
			}

		sentToKafkaTopic(Operations.DELETE_USER_FILES, userInfo);

		return ok();
	}

	private void removeSharesOfFile(ExtendedFileInfo file) {
		for (var userId : file.info().getSharedWith())
			userFiles.getOrDefault(userId, new UserFiles()).shared().remove(file.fileId());
	}

	private Queue<URI> candidateFileServers(ExtendedFileInfo file) {
		int MAX_SIZE = 3;
		Queue<URI> result = new ArrayDeque<>();

		if (file != null) {
			file.uris.forEach(uri -> result.add(uri));
		}

		FilesClients.all()
				.stream()
				.filter(u -> !result.contains(u))
				.limit(MAX_SIZE)
				.forEach(result::add);

		while (result.size() < MAX_SIZE)
			result.add(result.peek());

		Log.info("Candidate files servers: " + result + "\n");
		return result;
	}

	private FileCounts getFileCounts(URI uri, boolean create) {
		if (create)
			return fileCounts.computeIfAbsent(uri, FileCounts::new);
		else
			return fileCounts.getOrDefault(uri, new FileCounts(uri));
	}

	static record ExtendedFileInfo(ArrayList<URI> uris, String fileId, FileInfo info) {
	}

	static record SharingInfo(String fileId, String userIdShare) {
	}

	static record UserFiles(Set<String> owned, Set<String> shared) {

		UserFiles() {
			this(ConcurrentHashMap.newKeySet(), ConcurrentHashMap.newKeySet());
		}
	}

	static record FileCounts(URI uri, AtomicLong numFiles) {
		FileCounts(URI uri) {
			this(uri, new AtomicLong(0L));
		}

		static int ascending(FileCounts a, FileCounts b) {
			return Long.compare(a.numFiles().get(), b.numFiles().get());
		}
	}

	static record UserInfo(String userId, String password) {
	}

	private long sentToKafkaTopic(Operations operation, Object value) {

		String json = "";
		try {
			json = mapper.writeValueAsString(value);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		long version = sender.publish(operation.name(), json);
		new Thread(() -> {
			String result = "";
			while (result.equals("")) {
				result = sync.waitForResult(version);
			}
			System.out.printf("Op: %s, version: %s, result: %s\n", operation, version, result);
		}).start();

		return version;
	}

	@Override
	public void onReceive(ConsumerRecord<String, String> r) {
		var version = r.offset();
		System.out.printf("%s : processing: (%d, %s)\n", replicaId, version, r.value().toString());

		Operations opName = Operations.findByName(r.topic());
		System.out.println("---------------- " + opName.name());
		switch (opName) {
			case WRITE_FILE -> writeFileKafka(r.value());
			case DELETE_FILE -> deleteFileKafka(r.value());
			case DELETE_USER_FILES -> deleteUserFilesKafka(r.value());
			case GET_FILE -> throw new UnsupportedOperationException("Unimplemented case: " + opName);
			case LS_FILE -> throw new UnsupportedOperationException("Unimplemented case: " + opName);
			case SHARE_FILE -> shareFileKafka(r.value());
			case UNSHARE_FILE -> unshareFileKafka(r.value());
			default -> throw new IllegalArgumentException("Unexpected value: " + opName);
		}

		// var result = "result of " + r.value();
		sync.setResult(version, r.value().toString());
	}

	private void writeFileKafka(String value) {
		ExtendedFileInfo file = null;
		try {
			file = mapper.readValue(value, ExtendedFileInfo.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		String fileId = file.fileId;
		files.put(fileId, file);

		String userId = file.info.getOwner();
		UserFiles uf = userFiles.computeIfAbsent(userId, (k) -> new UserFiles());
		synchronized (uf) {
			uf.owned.add(fileId);
		}

	}

	private void deleteFileKafka(String value) {
		ExtendedFileInfo file = null;
		try {
			file = mapper.readValue(value, ExtendedFileInfo.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		String fileId = file.fileId;

		String userId = file.info.getOwner();
		var uf = userFiles.getOrDefault(userId, new UserFiles());

		synchronized (uf) {
			var info = files.remove(fileId);
			uf.owned().remove(fileId);

			if (info != null) {
				executor.execute(() -> {
					this.removeSharesOfFile(info);
				});
			}

		}
	}

	private void deleteUserFilesKafka(String value) {
		UserInfo file = null;
		try {
			file = mapper.readValue(value, UserInfo.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}
		users.invalidate(value);

		var fileIds = userFiles.remove(file.userId);
		if (fileIds != null)
			for (var id : fileIds.owned()) {
				var fileAux = files.remove(id);
				removeSharesOfFile(fileAux);
			}
	}

	private void shareFileKafka(String value) {
		SharingInfo info = null;
		try {
			info = mapper.readValue(value, SharingInfo.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		ExtendedFileInfo file = files.get(info.fileId);

		String userIdShare = info.userIdShare;
		String fileId = info.fileId;

		var uf = userFiles.computeIfAbsent(userIdShare, (k) -> new UserFiles());
		synchronized (uf) {
			uf.shared().add(fileId);
			file.info().getSharedWith().add(userIdShare);
		}
	}

	private void unshareFileKafka(String value) {
		SharingInfo info = null;
		try {
			info = mapper.readValue(value, SharingInfo.class);
		} catch (JsonProcessingException e) {
			e.printStackTrace();
		}

		ExtendedFileInfo file = files.get(info.fileId);

		String userIdShare = info.userIdShare;
		String fileId = info.fileId;

		var uf = userFiles.computeIfAbsent(userIdShare, (k) -> new UserFiles());
		synchronized (uf) {
			uf.shared().remove(fileId);
			file.info().getSharedWith().remove(userIdShare);
		}

	}

}