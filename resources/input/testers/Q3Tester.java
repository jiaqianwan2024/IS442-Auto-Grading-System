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
        {
            try {
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 10, 2));
                inputs.add(new Rectangle("R2", 5, 3));
                inputs.add(new Rectangle("R3", 20, 3));
                inputs.add(new Rectangle("R4", 500, 3));
     
                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[R3=>60.0:46.0, R1=>20.0:24.0, R2=>15.0:16.0]";

                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result.toString())) {
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
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Rectangle("R1", 100, 2));
                inputs.add(new Rectangle("R2", 200, 1));
                inputs.add(new Rectangle("R3", 4, 50));
                inputs.add(new Rectangle("R4", 40, 5));

                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[R2=>200.0:402.0, R1=>200.0:204.0, R3=>200.0:108.0, R4=>200.0:90.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result.toString())) {
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
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Circle("C1", 500));
                inputs.add(new Circle("C2", 3));
                inputs.add(new Rectangle("R1", 10000, 2));
                inputs.add(new Rectangle("R2", 5, 3));
           
     
                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[C2=>28.0:19.0, R2=>15.0:16.0]";
                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result.toString())) {
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
                ArrayList<Shape> inputs = new ArrayList<>();
                inputs.add(new Circle("C1", 5));
                inputs.add(new Circle("C2", 3));
                inputs.add(new Rectangle("R2", 8, 3));
                inputs.add(new Rectangle("R1", 12, 2));
                inputs.add(new Rectangle("R3", 4, 6));
     
                System.out.printf("Test %d: sortShapes(%s)%n", tcNum++, inputs);
                String expected = "[C1=>79.0:31.0, C2=>28.0:19.0, R1=>24.0:28.0, R2=>24.0:22.0, R3=>24.0:20.0]";

                List<Shape> result = sortShapes(inputs);
                System.out.printf("Expected  :|%s|%n", expected);
                System.out.printf("Actual    :|%s|%n", result);

                if (expected.equals(result.toString())) {
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