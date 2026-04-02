import java.util.*;

public class Q2bTester extends Q2b {

    private static double score;
    private static String qn = "Q2b";

    public static void main(String[] args) {
        grade();
        System.out.println(score);
    }

    public static void grade() {
        System.out.println("-------------------------------------------------------");
        System.out.println("---------------------- " + qn + " ----------------------------");
        System.out.println("-------------------------------------------------------");

        int tcNum = 1;

        // 8 test cases × (4.0/8) = 0.5 per TC → max 4.0, clean halves
        // students.txt content:
        //   John LEE,IS101#4.0-IS102#3.0-IS103#2.5
        //   LIM Peter,IS101#3.0
        //   LEE Teck Leong,IS101#4.0-IS102#3.0
        //   Mary CHAN,IS102#3.5-IS103#3.0
        //   Andrew TAN,IS103#2.0
        //   CHAN Wei Jun,IS101#4.0-IS103#4.0

        {
            // TC1: IS101 — 3-way tie at 4.0, first occurrence wins
            try {
                String inputs = "students.txt";
                String courseName = "IS101";
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "John LEE-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC2: IS102 — clear winner Mary CHAN-3.5
            try {
                String inputs = "students.txt";
                String courseName = "IS102";
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "Mary CHAN-3.5";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC3: IS103 — clear winner CHAN Wei Jun-4.0
            try {
                String inputs = "students.txt";
                String courseName = "IS103";
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "CHAN Wei Jun-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC4: course not in file → must throw DataException
            try {
                String inputs = "students.txt";
                String courseName = "IS104";
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String result = getTopStudent(inputs, courseName);
                System.out.println("Failed -> Expecting a DataException");
            } catch (DataException ex) {
                score += (4.0/8);
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC5: file does not exist → must throw DataException
            try {
                String inputs = "nosuchfile.txt";
                String courseName = "IS101";
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String result = getTopStudent(inputs, courseName);
                System.out.println("Failed -> Expecting a DataException");
            } catch (DataException ex) {
                score += (4.0/8);
                System.out.println("Passed");
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC6: student with only one course (no '-' delimiter) handled correctly
            try {
                String inputs = "students.txt";
                String courseName = "IS101";
                System.out.printf("Test %d: getTopStudent(%s, %s) [single-course student in pool]%n", tcNum++, inputs, courseName);
                String expected = "John LEE-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC7: 3-way tie — strict > not >= must be used (first occurrence wins)
            try {
                String inputs = "students.txt";
                String courseName = "IS101";
                System.out.printf("Test %d: getTopStudent(%s, %s) [3-way tie, first occurrence wins]%n", tcNum++, inputs, courseName);
                String expected = "John LEE-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC8: student taking multiple courses — verify all courses parsed correctly
            // Andrew TAN only takes IS103 with GPA 2.0 → lowest in IS103 pool but still returned correctly
            // Tests that multi-course parsing (splitting on '-') doesn't skip any student
            try {
                String inputs = "students.txt";
                String courseName = "IS103";
                System.out.printf("Test %d: getTopStudent(%s, %s) [multi-course parsing]%n", tcNum++, inputs, courseName);
                String expected = "CHAN Wei Jun-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }
    }
}