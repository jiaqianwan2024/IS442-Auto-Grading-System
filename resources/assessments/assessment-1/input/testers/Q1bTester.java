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

        // 8 test cases × (4.0/8) = 0.5 per TC → max 4.0, clean halves

        {
            // TC1: empty string → 0.0
            try {
                String input = "";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = 0.0;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC2: leading whitespace + negative decimal → -42.0001
            try {
                String input = "   -42.0001";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = -42.0001;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC3: second decimal point stops parsing → 123.45
            try {
                String input = "123.45.67";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = 123.45;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC4: negative scientific notation → -0.0415
            try {
                String input = " -4.15e-2";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = -0.0415;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC5: consecutive signs +-  → 0.0
            try {
                String input = " +-123.45";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = 0.0;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC6: stops at non-numeric alpha → -123.45
            try {
                String input = " -123.45abc";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = -123.45;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC7: non-integer exponent → 0.0
            try {
                String input = "3.14e+3.1";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = 0.0;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC8: uppercase E scientific notation → 314.0
            try {
                String input = "3.14E2";
                System.out.printf("Test %d: stringToDouble(\"%s\")%n", tcNum++, input);
                double expected = 314.0;
                double result = stringToDouble(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (Double.compare(expected, result) == 0) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }
    }
}