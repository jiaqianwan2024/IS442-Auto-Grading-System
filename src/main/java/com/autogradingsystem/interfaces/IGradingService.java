package com.autogradingsystem.interfaces;

import com.autogradingsystem.model.Student;
import java.io.File;

public interface IGradingService {
    void gradeStudent(Student student, File unzippedFolder);
}