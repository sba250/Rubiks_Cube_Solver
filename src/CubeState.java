package rubikscube;

public class CubeState {

    public static char[][][] copyCube(char[][][] o) {
        char[][][] c = new char[6][3][3];
        for (int f = 0; f < 6; f++)
            for (int r = 0; r < 3; r++)
                for (int col = 0; col < 3; col++)
                    c[f][r][col] = o[f][r][col];
        return c;
    }

    public static long hashCube(char[][][] c) {
        long h = 0;
        for (int f = 0; f < 6; f++)
            for (int r = 0; r < 3; r++)
                for (int col = 0; col < 3; col++)
                    h = h * 31 + c[f][r][col];
        return h;
    }
}