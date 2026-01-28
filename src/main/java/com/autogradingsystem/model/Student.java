package com.autogradingsystem.model;

public class Student {
    private String studentId;
    private String name;
    private double finalScore;

    public Student(String name, String studentId) {
        this.name = name;
        this.studentId = studentId;
        this.finalScore = 0.0;
    }
    
    public String getStudentId() { return studentId; }
    public String getName() { return name; }
    public double getFinalScore() { return finalScore; }
}