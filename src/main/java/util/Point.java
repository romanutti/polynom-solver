package util;

import java.awt.geom.Point2D;

public class Point extends Point2D.Double{

    public Point(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public Point(Point p) {
        this(p.x, p.y);
    }

    public Point relTo(Point p) {
        return new Point(x - p.x, y - p.y);
    }

    public void makeRelTo(Point p) {
        x -= p.x;
        y -= p.y;
    }

    public Point moved(double x0, double y0) {
        return new Point(x + x0, y + y0);
    }

    public Point reversed() {
        return new Point(-x, -y);
    }

    public boolean isLower(Point p) {
        return y < p.y || y == p.y && x < p.x;
    }

    public double mdist() {
        return Math.abs(x) + Math.abs(y);
    }

    public double mdist(Point p) {
        return relTo(p).mdist();
    }

    public boolean isFurther(Point p) {
        return mdist() > p.mdist();
    }

    public boolean isBetween(Point p0, Point p1) {
        return p0.mdist(p1) >= mdist(p0) + mdist(p1);
    }

    public double cross(Point p) {
        return x * p.y - p.x * y;
    }

    public boolean isLess(Point p) {
        double f = cross(p);
        return f > 0 || f == 0 && isFurther(p);
    }

    public double area2(Point p0, Point p1) {
        return p0.relTo(this).cross(p1.relTo(this));
    }

    public boolean isConvex(Point p0, Point p1) {
        double f = area2(p0, p1);
        return f < 0 || f == 0 && !isBetween(p0, p1);
    }

    public Vec2 toVec(){
        return new Vec2(x, y);
    }
}
