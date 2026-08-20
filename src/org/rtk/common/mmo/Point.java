package org.rtk.common.mmo;

/** Port of struct point (common/mmo.h): peta + koordinat. */
public final class Point {
    public int m; // unsigned short: id peta
    public int x;
    public int y;

    public Point() {
    }

    public Point(int m, int x, int y) {
        this.m = m;
        this.x = x;
        this.y = y;
    }

    @Override
    public String toString() {
        return "(" + m + ":" + x + "," + y + ")";
    }
}
