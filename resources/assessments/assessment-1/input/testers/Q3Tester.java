import java.util.*;

public class Q3Tester extends Q3 {

    private static double score;
    private static String qn = "Q3";

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
        // Dropped TC8 (single shape) — covered by TC1 which also produces a 1-element result

        {
            // TC1: filter removes large shapes, 1 remains
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 1000, 2));
                inputs.add(new Rectangle("R2", 4, 2.5));
                inputs.add(new Circle("C1", 50));

                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[R2=>10.0:13.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC2: descending area sort — no ties
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 10, 2));
                inputs.add(new Rectangle("R2", 4, 2.5));
                inputs.add(new Rectangle("R3", 5, 6));

                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[R3=>30.0:22.0, R1=>20.0:24.0, R2=>10.0:13.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC3: same area — tiebreak by descending perimeter
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 8, 3));
                inputs.add(new Rectangle("R2", 6, 4));
                inputs.add(new Rectangle("R3", 12, 2));

                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[R3=>24.0:28.0, R1=>24.0:22.0, R2=>24.0:20.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC4: mixed rectangles and circles
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 8, 3));
                inputs.add(new Rectangle("R2", 12, 2));
                inputs.add(new Circle("C1", 10));
                inputs.add(new Circle("C2", 1));

                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[C1=>314.0:63.0, R2=>24.0:28.0, R1=>24.0:22.0, C2=>3.0:6.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC5: empty list — must return [] not null or throw
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                System.out.printf("Test %d: sortShapes(%s) [empty list]%n", tcNum++, inputs);
                String expected = "[]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC6: all shapes area > 1000 — all filtered, return []
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Circle("C1", 20));
                inputs.add(new Circle("C2", 25));
                inputs.add(new Rectangle("R1", 50, 30));

                System.out.printf("Test %d: sortShapes(%s) [all filtered out]%n", tcNum++, inputs);
                String expected = "[]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC7: area exactly 1000 must be INCLUDED (filter is <= 1000)
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 50, 20));
                inputs.add(new Rectangle("R2", 100, 20));

                System.out.printf("Test %d: sortShapes(%s) [boundary area=1000 included]%n", tcNum++, inputs);
                String expected = "[R1=>1000.0:140.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }

        {
            // TC8: circles only — comparator handles Circle area/perimeter correctly
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Circle("C1", 5));
                inputs.add(new Circle("C2", 3));
                inputs.add(new Circle("C3", 10));

                System.out.printf("Test %d: sortShapes(%s) [circles only]%n", tcNum++, inputs);
                String expected = "[C3=>314.0:63.0, C1=>79.0:31.0, C2=>28.0:19.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);
                if (expected.equals(result.toString())) { score += (4.0/8); System.out.println("Passed"); }
                else { System.out.println("Failed"); }
            } catch (Exception e) { System.out.println("Failed -> Exception"); e.printStackTrace(); }
            System.out.println("-------------------------------------------------------");
        }
    }
}