package testapp;

import java.util.List;

/**
 * Test fixture for different signatures verification
 */
public class Signatures {
    public void entry() {
        withCustomClass(this);
        withCustomArray(new Signatures[0]);
        withMixed(1, "x", this);
        withGenerics(List.of());
        withMultiDimArray(new int[0][0]);
        withPrimitives(1, 2L, 3.0);
        Signatures made = makeSelf();
    }

    public void withCustomClass(Signatures s) {}

    public void withCustomArray(Signatures[] arr) {}

    public void withMixed(int n, String s, Signatures self) {}

    public void withGenerics(List<String> items) {}

    public void withMultiDimArray(int[][] grid) {}

    public int withPrimitives(int a, long b, double c) {
        return 0;
    }

    public Signatures makeSelf() {
        return this;
    }
}
