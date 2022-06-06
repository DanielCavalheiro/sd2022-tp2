package tp2.impl.servers.common;

import static tp2.api.service.java.Result.error;
import static tp2.api.service.java.Result.ok;
import static tp2.api.service.java.Result.ErrorCode.INTERNAL_ERROR;
import static tp2.api.service.java.Result.ErrorCode.NOT_FOUND;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Comparator;

import tp2.api.service.java.Files;
import tp2.api.service.java.Result;
import util.Hash;
import util.IO;
import util.Token;

public class JavaFiles implements Files {

	static final String DELIMITER = "$$$";
	public static final String ROOT = "/tmp/";

	public JavaFiles() {
		new File(ROOT).mkdirs();
	}

	@Override
	public Result<byte[]> getFile(String fileId, String token) {
		// System.out.println(token);
		// String[] tokenAndTime = token.split("\\$\\$\\$");
		// System.out.println("----------" + tokenAndTime[0]);
		// System.out.println("----------" + tokenAndTime[1]);
		// String actualToken = tokenAndTime[0];
		// String time = tokenAndTime[1];

		// if (!Hash.of(Token.get(), time, fileId).equals(actualToken))
		// 	return error(Result.ErrorCode.FORBIDDEN);

		fileId = fileId.replace(DELIMITER, "/");
		byte[] data = IO.read(new File(ROOT + fileId));
		return data != null ? ok(data) : error(NOT_FOUND);
	}

	@Override
	public Result<Void> deleteFile(String fileId, String token) {
		String[] tokenAndTime = token.split("\\$\\$\\$");
		String actualToken = tokenAndTime[0];
		String time = tokenAndTime[1];

		if (!Hash.of(Token.get(), time, fileId).equals(actualToken))
			return error(Result.ErrorCode.FORBIDDEN);

		fileId = fileId.replace(DELIMITER, "/");
		boolean res = IO.delete(new File(ROOT + fileId));
		return res ? ok() : error(NOT_FOUND);
	}

	@Override
	public Result<Void> writeFile(String fileId, byte[] data, String token) {
		String[] tokenAndTime = token.split("\\$\\$\\$");
		String actualToken = tokenAndTime[0];
		String time = tokenAndTime[1];

		if (!Hash.of(Token.get(), time, fileId).equals(actualToken))
			return error(Result.ErrorCode.FORBIDDEN);

		fileId = fileId.replace(DELIMITER, "/");
		File file = new File(ROOT + fileId);
		file.getParentFile().mkdirs();
		IO.write(file, data);
		return ok();
	}

	@Override
	public Result<Void> deleteUserFiles(String userId, String token) {
		String[] tokenAndTime = token.split("\\$\\$\\$");
		String actualToken = tokenAndTime[0];
		String time = tokenAndTime[1];

		if (!Hash.of(Token.get(), time, userId).equals(actualToken))
			return error(Result.ErrorCode.FORBIDDEN);

		File file = new File(ROOT + userId);
		try {
			java.nio.file.Files.walk(file.toPath())
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);
		} catch (IOException e) {
			e.printStackTrace();
			return error(INTERNAL_ERROR);
		}
		return ok();
	}

	public static String fileId(String filename, String userId) {
		return userId + JavaFiles.DELIMITER + filename;
	}

}
