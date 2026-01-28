package com.autogradingsystem.interfaces;

import java.io.File;
import java.io.IOException;
import java.util.List;

public interface IFileService {
    List<File> findStudentZipFiles(String rootPath);
    boolean unzipSubmission(File zipFile, String destPath) throws IOException;
    void deleteDirectory(File dir);
}