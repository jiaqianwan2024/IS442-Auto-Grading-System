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
            // TC1: Leading/trailing spaces trimmed, punctuation stays as suffix
            // " hello, SMU! " -> "hello," letters=hello sorted=ehllo -> "ehllo,"  "SMU!" letters=SMU sorted=MSU -> "MSU!"
            try {
                String input = " hello, SMU! ";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "ehllo, MSU!";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC2: Multiple spaces between words preserved, punctuation suffix preserved
            // "hello world, this     is great!"
            // hello->ehllo  world,->dlorw,  this->hist  is->is  great!->aegrt!
            try {
                String input = "hello world, this     is great!";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "ehllo dlorw, hist     is aegrt!";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC3: Leading and trailing spaces trimmed, simple words no punctuation
            // " quick brown fox " -> "cikqu bnorw fox"
            try {
                String input = " quick brown fox ";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "cikqu bnorw fox";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC4: Empty string — must return "" without crashing
            try {
                String input = "";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC5: All spaces — no words after trim, must return ""
            try {
                String input = "   ";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC6: Uppercase letters sorted by ASCII (uppercase A-Z = 65-90, sorts before lowercase)
            // "HELLO" H,E,L,L,O -> sorted E,H,L,L,O -> "EHLLO"
            // "WORLD" W,O,R,L,D -> sorted D,L,O,R,W -> "DLORW"
            try {
                String input = "HELLO WORLD";
                System.out.printf("Test %d: reorderWordsInSentence(\"%s\")%n", tcNum++, input);
                String expected = "EHLLO DLORW";
                String result = reorderWordsInSentence(input);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result)) { score += (3.0/6); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }
    }
}
