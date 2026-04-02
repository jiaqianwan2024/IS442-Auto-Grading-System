import java.util.*;

public class Q1aTester extends Q1a {

    private static double score;
    private static String qn = "Q1a";

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
                ArrayList<String> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add("alice");
                inputs.add("ben");
                inputs.add("cathy");

                System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
                ArrayList<String> expected = new ArrayList<>(Arrays.asList("alice", "ben", "cathy"));
                ArrayList<String> result = getIsogramWords(inputs);
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

        {
            try {
                ArrayList<String> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add("ellen");
                inputs.add("alan");
                inputs.add("zoe");

                System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
                ArrayList<String> expected = new ArrayList<>(Arrays.asList("zoe"));
                ArrayList<String> result = getIsogramWords(inputs);
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

        {
            try {
                ArrayList<String> inputs = new ArrayList<>();
                inputs = new ArrayList<>();
                inputs.add("Elle");
                inputs.add("alan");
                inputs.add("Daniel");
                inputs.add("Canny");

                System.out.printf("Test %d: getIsogramWords(%s)%n", tcNum++, inputs);
                ArrayList<String> expected = new ArrayList<>(Arrays.asList("Daniel"));
                ArrayList<String> result = getIsogramWords(inputs);
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