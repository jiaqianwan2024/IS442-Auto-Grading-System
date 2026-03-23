import java.util.*;

public class Q1bTester extends Q1b {

    private static double score;
    private static String qn = "Q1b";

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
                ArrayList<Object> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add(2);
                inputs.add(4);
               
                System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
                int expected = 6;
                int result = getSumOfEvenIntegers(inputs);
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
                ArrayList<Object> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add(2);
                inputs.add(false);
                inputs.add(4.0);
                inputs.add("two");
                
                System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
                int expected = 2;
                int result = getSumOfEvenIntegers(inputs);
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
                ArrayList<Object> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add(9);
                inputs.add(2);
                inputs.add(10);
                inputs.add(3);
                inputs.add(5);
                inputs.add(8);
                
                System.out.printf("Test %d: getSumOfEvenIntegers(%s)%n", tcNum++, inputs);
                int expected = 20;
                int result = getSumOfEvenIntegers(inputs);
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