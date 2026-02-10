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
        {
            try {
                String inputs = "is442.txt";
                String courseName = "IS101";
                
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String result = getTopStudent(inputs, courseName);
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
                score += 1;
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");

            try {
                String inputs = "studentstester.txt";
                String courseName = "SMU101";
                
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String result = getTopStudent(inputs, courseName);
                System.out.println("Failed -> Expecting a Data Exception");
            } catch (DataException ex){
                System.out.println("Passed");
                score += 1;
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");

            try {
                String inputs = "studentstester.txt";
                String courseName = "CS101";
                
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "LEE Teck Yang-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result)) {
                    score += 1;
                    System.out.println("Passed");
                } else {
                    System.out.println("Failed");
                }
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");

            try {
                String inputs = "studentstester.txt";
                String courseName = "CS102";
                
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "Jason LEE-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result)) {
                    score += 1;
                    System.out.println("Passed");
                } else {
                    System.out.println("Failed");
                }
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");

            try {
                String inputs = "studentstester.txt";
                String courseName = "CS103";
                
                System.out.printf("Test %d: getTopStudent(%s, %s)%n", tcNum++, inputs, courseName);
                String expected = "TAY Evelyn-4.0";
                String result = getTopStudent(inputs, courseName);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result)) {
                    score += 1;
                    System.out.println("Passed");
                } else {
                    System.out.println("Failed");
                }
            } catch (Exception e) {
                System.out.println("Failed -> Exception");
                e.printStackTrace();
            }
            System.out.println("-------------------------------------------------------");
            
        }
    }
}