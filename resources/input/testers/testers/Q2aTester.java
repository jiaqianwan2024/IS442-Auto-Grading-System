import java.util.*;

public class Q2aTester extends Q2a {

    private static double score;
    private static String qn = "Q2a";

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
                String surname = "LEE";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = -1.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected == result) {
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
                String inputs = "personstester.txt";
                String surname = "TAY";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 30.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected == result) {
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
                String inputs = "personstester.txt";
                String surname = "LEE";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 37.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected == result) {
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
                String inputs = "personstester.txt";
                String surname = "Wong";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 26.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected == result) {
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
                String inputs = "personstester.txt";
                String surname = "Ong";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 0.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected == result) {
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