import util.Point;
import util.Vec2;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.Scanner;
import java.util.concurrent.*;

public class Solver implements Callable {
    /** Max time to be used */
    static double timeLimit;
    /** Number of polygons */
    static int polygonCount;
    /** Polygons */
    static Polygon[] polygons;
    /** Current polygon */
    static Polygon curPolygon;
    /** Stepsize */
    static final float THETA = 0.3f;
    /** Min stepsize */
    static final float EPS = 0.0001f;

    // Attributes to read input files
    BufferedReader inp;
    private int inpPos = 0;
    private String inpLine;
    static PrintWriter out;

    public Solver() throws Exception {
        // Load file
        this.loadFile();

        // Get timelimit
        timeLimit = Double.valueOf(next());

        // Get number of polygons
        polygonCount = Integer.valueOf(next());

        // Initialize polygon
        polygons = new Polygon[polygonCount];
        for (int i = 0; i < polygonCount; i++) {
            // Get number of points
            int pointCount = Integer.valueOf(next());

            // Add points
            util.Point[] points = new util.Point[pointCount];
            for (int j = 0; j < pointCount; j++) {
                String tupel = next();
                double x = Double.valueOf(tupel.split(",")[0]);
                double y = Double.valueOf(tupel.split(",")[1]);
                points[j] = new util.Point(x, y);
            }

            // Add edges
            Vec2[] edges = getEdges(pointCount, points);

            // Add polygon
            polygons[i] = new Polygon(pointCount, points, edges);
        }
    }

    /**
     * Finds max. size square per polygon.
     */
    public static void main(String[] args) throws Exception {
        // Create outputfile
        out = new PrintWriter("polygons.out");

        // Create new polygon
        Solver solver = new Solver();

        // Start execution
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        Future<Boolean> control
                = executorService.submit(solver);
        try {
            Boolean finished = control.get((long) (timeLimit * 1000), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            // Timelimit expired, cancel the job
            control.cancel(true);
            System.out.println("Timelimit expired!");

            // Write current solution to output
            out.println(curPolygon.solution.p1.x + "," + curPolygon.solution.p1.y + " " + curPolygon.solution.p2.x + ","
                    + curPolygon.solution.p2.y + " " + curPolygon.solution.p3.x + "," + curPolygon.solution.p3.y + " "
                    + curPolygon.solution.p4.x + "," + curPolygon.solution.p4.y + " ");
        } finally {
            out.flush();
            out.close();
        }

        // Render polygon
        Scanner in = new Scanner(System.in);
        System.out.println("Enter number of polygon to render");
        int p = in.nextInt();
        polygons[p-1].render();
    }

    @Override
    public Boolean call() {
        // Iterate over all polygons
        for (int i = 0; i < polygonCount; i++) {
            curPolygon = polygons[i];
            int pointCount = curPolygon.pointCount;

            // Iterate over all edges
            for (int j = 0; j < pointCount; j++) {
                int k = j == pointCount - 1 ? 0 : j + 1;

                // ###############################################################
                // # Initialize rect
                // ###############################################################
                Point A = curPolygon.points[j];
                Point B = curPolygon.points[k];

                Vec2 AB = curPolygon.edges[j];
                Vec2 BC = AB.rotate(B);

                Point C = new Point(B.x + BC.x, B.y + BC.y);
                Point D = new Point(A.x + BC.x, A.y + BC.y);

                Rect curRect = new Rect(A, B, C, D);

                // ###############################################################
                // # Step 1: Shrink continuously until rectangle fits in polygon
                // ###############################################################
                while (harmsConstraint(curPolygon, curRect, A, B, C, D)) {
                    // Shrink rect
                    AB = AB.scale(THETA);
                    A = A.moved(AB.x, AB.y);
                    B = A.moved(AB.x, AB.y);
                    BC = AB.rotate(B);
                    C = B.moved(BC.x, BC.y);
                    D = A.moved(BC.x, BC.y);

                    curRect = new Rect(A, B, C, D);
                }

                // Compare with best solution
                if (curPolygon.getArea() < curRect.getArea()) {
                    curPolygon.solution = curRect;
                }

                // ###############################################################
                // # Step 2: Enlarge in variable stepsize until rectangle touches border
                // ###############################################################
                Rect validRect = curRect;
                float theta = THETA;
                float eps = EPS;
                Vec2 delta = null;
                Point A_temp = A;
                Point B_temp = B;
                Point C_temp = C;
                Point D_temp = D;
                Vec2 AB_temp = AB;
                Vec2 BC_temp = BC;

                while (theta > eps) {
                    AB = B.relTo(A).toVec();
                    BC = AB.rotate(B);

                    // Enlarge rect
                    delta = AB.scale(theta);
                    B = A.moved(AB.x + delta.x, AB.y + delta.y);
                    A = A.moved(-delta.x, -delta.y);

                    delta = BC.scale(theta);
                    C = B.moved(BC.x + 2 * delta.x, BC.y + 2 * delta.y);
                    D = A.moved(BC.x + 2 * delta.x, BC.y + 2 * delta.y);

                    // Save last valid rect
                    if (!harmsConstraint(curPolygon, curRect, A, B, C, D)) {
                        // Set valid vars
                        validRect = new Rect(A, B, C, D);
                        A_temp = A;
                        B_temp = B;
                        C_temp = C;
                        D_temp = D;
                        AB_temp = AB;
                        BC_temp = BC;
                    } else {
                        // Reset vars
                        curRect = validRect;
                        A = A_temp;
                        B = B_temp;
                        C = C_temp;
                        D = D_temp;
                        AB = AB_temp;
                        BC = BC_temp;

                        // Adapt theta
                        theta = 0.1f * theta;
                    }
                }
                curRect = validRect;
                // Compare with best solution
                if (curPolygon.getArea() < curRect.getArea()) {
                    curPolygon.solution = curRect;
                }

                // ###############################################################
                // # Step 3a: Move and enlarge to right side
                // ###############################################################
                Rect rRect = moveAndMaximizeRect(curPolygon, A, B, AB, BC, C, D, curRect, true);
                // Compare with best solution
                if (curPolygon.getArea() <= rRect.getArea()) {
                    curPolygon.solution = rRect;
                }
                // ###############################################################
                // # Step 3b: Move and enlarge to left side
                // ###############################################################
                Rect lRect = moveAndMaximizeRect(curPolygon, A, B, AB, BC, C, D, curRect, false);
                // Compare with best solution
                if (curPolygon.getArea() <= lRect.getArea()) {
                    curPolygon.solution = lRect;
                }
            }

            // Write solution to output
            out.println(curPolygon.solution.p1.x + "," + curPolygon.solution.p1.y + " " + curPolygon.solution.p2.x + ","
                    + curPolygon.solution.p2.y + " " + curPolygon.solution.p3.x + "," + curPolygon.solution.p3.y + " "
                    + curPolygon.solution.p4.x + "," + curPolygon.solution.p4.y + " ");
            out.flush();
        }

        return true;
    }

    /**
     * Move rectangle along AB axis and try to enlarge.
     */
    private static Rect moveAndMaximizeRect(Polygon curPolygon, util.Point a, util.Point b, Vec2 AB, Vec2 BC,
            util.Point c, util.Point d, Rect curRect, boolean moveRight) {
        Rect validRect;
        float theta;
        float eps;
        Vec2 delta;
        util.Point A_temp;
        util.Point B_temp;
        util.Point C_temp;
        util.Point D_temp;
        Vec2 AB_temp;
        Vec2 BC_temp;
        validRect = curRect;
        theta = THETA;
        eps = EPS;
        A_temp = a;
        B_temp = b;
        C_temp = c;
        D_temp = d;
        AB_temp = AB;
        BC_temp = BC;
        while (theta > eps) {
            AB = b.relTo(a).toVec();
            BC = AB.rotate(b);

            delta = AB.scale(theta);
            if (moveRight) {
                // Move rect to right
                a = a.moved(delta.x, delta.y);
                b = b.moved(delta.x, delta.y);
                c = c.moved(delta.x, delta.y);
                d = d.moved(delta.x, delta.y);
            } else {
                // Move rect to left
                a = a.moved(-delta.x, -delta.y);
                b = b.moved(-delta.x, -delta.y);
                c = c.moved(-delta.x, -delta.y);
                d = d.moved(-delta.x, -delta.y);
            }

            // Save last valid rect
            if (!harmsConstraint(curPolygon, curRect, a, b, c, d)) {
                // Set valid vars
                validRect = new Rect(a, b, c, d);
                A_temp = a;
                B_temp = b;
                C_temp = c;
                D_temp = d;
                AB_temp = AB;
                BC_temp = BC;

                // Try to enlarge rect
                float theta_sub = THETA;
                eps = EPS;
                delta = null;
                while (theta_sub > eps) {
                    AB = b.relTo(a).toVec();
                    BC = c.relTo(b).toVec();

                    // Enlarge rect
                    delta = AB.scale(theta_sub);
                    b = a.moved(AB.x + delta.x, AB.y + delta.y);
                    a = a.moved(-delta.x, -delta.y);

                    delta = BC.scale(theta_sub);
                    c = b.moved(BC.x + 2 * delta.x, BC.y + 2 * delta.y);
                    d = a.moved(BC.x + 2 * delta.x, BC.y + 2 * delta.y);

                    // Save last valid rect
                    if (!harmsConstraint(curPolygon, curRect, a, b, c, d)) {
                        if (curPolygon.getArea() < curRect.getArea()) {
                            curPolygon.solution = curRect;
                        }
                        // Set valid vars
                        validRect = new Rect(a, b, c, d);
                        A_temp = a;
                        B_temp = b;
                        C_temp = c;
                        D_temp = d;
                        AB_temp = AB;
                        BC_temp = BC;
                    } else {
                        // Reset vars
                        curRect = validRect;
                        a = A_temp;
                        b = B_temp;
                        c = C_temp;
                        d = D_temp;
                        AB = AB_temp;
                        BC = BC_temp;

                        // Adapt theta
                        theta_sub = 0.1f * theta_sub;
                    }
                }
                curRect = validRect;
                if (curPolygon.getArea() <= curRect.getArea()) {
                    curPolygon.solution = curRect;
                }

            } else {
                // Reset vars
                curRect = validRect;
                a = A_temp;
                b = B_temp;
                c = C_temp;
                d = D_temp;
                AB = AB_temp;
                BC = BC_temp;

                // Adapt theta
                theta = 0.1f * theta;
            }
        }
        curRect = validRect;
        return curRect;
    }

    /**
     * Check if lines intersect with one of the edges in polygon and if all points of the polygon inside the square
     */
    private static boolean harmsConstraint(Polygon curPolygon, Rect curRect, util.Point a, util.Point b, util.Point c,
            util.Point d) {
        // ###############################################################
        // Check 1: Does one of the lines intersect with one of the edges in polygon?
        // ###############################################################
        // Check AB
        if (doesIntersect(a, b, curPolygon))
            return true;
        // Check BC
        if (doesIntersect(b, c, curPolygon))
            return true;
        // Check CD
        if (doesIntersect(c, d, curPolygon))
            return true;
        // Check DA
        if (doesIntersect(d, a, curPolygon))
            return true;

        // ###############################################################
        // Check 2: Is a point of the polygon inside the square?
        // ###############################################################
        int pointsCoveredCount = 0;
        for (int i = 0; i < curPolygon.pointCount; i++) {
            // Dont check points on base line of square
            if (curPolygon.points[i].x == a.x && curPolygon.points[i].y == a.y) {
                pointsCoveredCount += 1;
                continue;
            }
            if (curPolygon.points[i].x == b.x && curPolygon.points[i].y == b.y) {
                pointsCoveredCount += 1;
                continue;
            }
            if (curPolygon.points[i].x == c.x && curPolygon.points[i].y == c.y) {
                pointsCoveredCount += 1;
                continue;
            }
            if (curPolygon.points[i].x == d.x && curPolygon.points[i].y == d.y) {
                pointsCoveredCount += 1;
                continue;
            }
            // Check if corner point inside
            if (isInside(curRect, curPolygon.points[i], a, b, c, d))
                return true;
        }

        // ###############################################################
        // Check 3: Is the polygon a triangle that covers half the area of the rectangle?
        // ###############################################################
        if (pointsCoveredCount == 3 && curPolygon.pointCount == 3)
            return true;

        return false;
    }

    /**
     * Check if point of the polygon inside the square
     */
    private static boolean isInside(Rect curRect, util.Point P, util.Point A, util.Point B, util.Point C,
            util.Point D) {
        double areaRect = curRect.getArea();

        // Source: https://stackoverflow.com/questions/17136084/checking-if-a-point-is-inside-a-rotated-rectangle/17146376
        double areaAPD = Math.abs((P.x * A.y - A.x * P.y) + (D.x * P.y - P.x * D.y) + (A.x * D.y - D.x * A.y)) / 2;
        double areaDPC = Math.abs((P.x * D.y - D.x * P.y) + (C.x * P.y - P.x * C.y) + (D.x * C.y - C.x * D.y)) / 2;
        double areaCPB = Math.abs((P.x * C.y - C.x * P.y) + (B.x * P.y - P.x * B.y) + (C.x * B.y - B.x * C.y)) / 2;
        double areaPBA = Math.abs((B.x * P.y - P.x * B.y) + (A.x * B.y - B.x * A.y) + (P.x * A.y - A.x * P.y)) / 2;

        double areaSum = areaAPD + areaDPC + areaCPB + areaPBA;

        return areaSum <= areaRect;

    }

    /**
     * Check if line between a and b intersects with polygon
     */
    private static boolean doesIntersect(util.Point a, util.Point b, Polygon curPolygon) {
        Vec2 A = a.toVec();
        Vec2 B = b.toVec();

        // Iterate over all edges
        boolean intersects;
        for (int j = 0; j < curPolygon.pointCount; j++) {
            int k = j == curPolygon.pointCount - 1 ? 0 : j + 1;
            Vec2 C = curPolygon.points[j].toVec();
            Vec2 D = curPolygon.points[k].toVec();

            intersects = Vec2.ccw(A, C, D) * Vec2.ccw(B, C, D) < 0 && Vec2.ccw(C, A, B) * Vec2.ccw(D, A, B) < 0;
            if (intersects)
                return true;
        }
        return false;
    }

    /**
     * Calculate edges of polygon.
     */
    private static Vec2[] getEdges(int pointCount, util.Point[] points) {
        // Calculate edges
        Vec2[] edges = new Vec2[pointCount];
        for (int j = 0; j < pointCount; j++) {
            // Start at point  0 after last point
            int k = j == pointCount - 1 ? 0 : j + 1;

            double dx = points[k].relTo(points[j]).x;
            double dy = points[k].relTo(points[j]).y;

            edges[j] = new Vec2(dx, dy);
        }
        return edges;
    }

    private void loadFile() throws IOException {
        // Read file
        inp = new BufferedReader(new FileReader(getFileFromResources("polygons.in")));
        inpLine = inp.readLine();
    }

    private String next() throws Exception {
        int nextPos = inpLine.indexOf(' ', inpPos + 1);
        String token = inpLine.substring(inpPos, nextPos == -1 ? inpLine.length() : nextPos);
        if (nextPos == -1)
            inpLine = inp.readLine();
        inpPos = nextPos + 1;
        return token;
    }

    private File getFileFromResources(String fileName) {
        ClassLoader classLoader = this.getClass().getClassLoader();

        URL resource = classLoader.getResource(fileName);
        return new File(resource.getFile());
    }

    static class Polygon extends JFrame {
        // Number of points
        int pointCount;
        // Points of polygon
        util.Point[] points;
        // Edges of polygon
        Vec2[] edges;
        // Best solution
        Rect solution;

        public double getArea() {
            if (solution == null)
                return 0;
            return solution.getArea();
        }

        public Polygon(int pointCount, util.Point[] points, Vec2[] edges) {
            super();

            this.pointCount = pointCount;
            this.points = points;
            this.edges = edges;
        }

        public void render() {
            setSize(500, 400);
            setResizable(true);
            setVisible(true);
            setDefaultCloseOperation(DISPOSE_ON_CLOSE);
            getContentPane().add(new JPanel() {
                @Override public void paint(Graphics _g) {
                    super.paint(_g);

                    int[] polyX = new int[pointCount];
                    int[] polyY = new int[pointCount];
                    for (int i = 0; i < pointCount; i++) {
                        polyX[i] = (int) points[i].x;
                        polyY[i] = (int) points[i].y;
                    }

                    _g.setColor(Color.RED);
                    _g.fillPolygon(polyX, polyY, polyX.length);

                    _g.setColor(Color.BLACK);
                    _g.drawPolygon(polyX, polyY, polyX.length);

                    if (solution != null) {
                        int[] rectX = new int[4];
                        rectX[0] = (int) solution.p1.x;
                        rectX[1] = (int) solution.p2.x;
                        rectX[2] = (int) solution.p3.x;
                        rectX[3] = (int) solution.p4.x;

                        int[] rectY = new int[4];
                        rectY[0] = (int) solution.p1.y;
                        rectY[1] = (int) solution.p2.y;
                        rectY[2] = (int) solution.p3.y;
                        rectY[3] = (int) solution.p4.y;

                        _g.setColor(Color.BLUE);
                        _g.drawPolygon(rectX, rectY, rectX.length);
                    }
                }
            });
        }
    }

    static class Rect {
        util.Point p1;
        util.Point p2;
        util.Point p3;
        util.Point p4;

        public Rect(util.Point p1, util.Point p2, util.Point p3, Point p4) {
            this.p1 = p1;
            this.p2 = p2;
            this.p3 = p3;
            this.p4 = p4;
        }

        public double getArea() {
            return p1.relTo(p2).toVec().lengthSquared();
        }
    }

}
