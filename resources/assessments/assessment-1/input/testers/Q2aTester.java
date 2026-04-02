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

        // 8 test cases × (4.0/8) = 0.5 per TC → max 4.0, clean halves
        // persons.txt content:
        //   John LEE-28
        //   LIM Peter-40
        //   LEE Teck Leong-44
        //   Mary CHAN-30
        //   Andrew TAN-50
        //   CHAN Wei Jun-24
        //   TEO Wong Whee-30
        //   Jason WONG-25

        {
            // TC1: surname as last word, multiple matches → average
            // John LEE (28) + LEE Teck Leong (44) = 72 / 2 = 36.0
            try {
                String inputs = "persons.txt";
                String surname = "LEE";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 36.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC2: surname as last word, single match
            // Jason WONG (25) only → 25.0
            try {
                String inputs = "persons.txt";
                String surname = "WONG";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 25.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC3: case-insensitive match + first/last word both — 2 matches
            // Mary CHAN (30) + CHAN Wei Jun (24) = 54 / 2 = 27.0
            try {
                String inputs = "persons.txt";
                String surname = "Chan";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 27.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC4: no match → 0.0
            try {
                String inputs = "persons.txt";
                String surname = "Ong";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 0.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC5: file does not exist → -1.0
            try {
                String inputs = "nosuchfile.txt";
                String surname = "Lee";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = -1.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC6: surname as FIRST word — TEO Wong Whee, TEO matches as first word
            // Only one match: TEO (age 30) → 30.0
            try {
                String inputs = "persons.txt";
                String surname = "TEO";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 30.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC7: middle word must NOT match as surname
            // "TEO Wong Whee" — "Wong" is MIDDLE word, must not match
            // Only "Jason WONG" (last word) → 25.0
            // Wrong implementation matching middle words would return (25+30)/2 = 27.5
            try {
                String inputs = "persons.txt";
                String surname = "Wong";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 25.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC8: single match via last word — Andrew TAN → 50.0
            try {
                String inputs = "persons.txt";
                String surname = "TAN";
                System.out.printf("Test %d: getAverageAge(%s, %s)%n", tcNum++, inputs, surname);
                double expected = 50.0;
                double result = getAverageAge(inputs, surname);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected == result) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }
    }
}