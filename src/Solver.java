package rubikscube;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Solver {
    // Face indices: U=0, D=1, F=2, B=3, L=4, R=5
    static final int U = 0, D = 1, F = 2, B = 3, L = 4, R = 5;

    // Color to face mapping
    static final Map<Character, Integer> COLOR_TO_FACE = new HashMap<>();
    static {
        COLOR_TO_FACE.put('W', U);  // White
        COLOR_TO_FACE.put('Y', D);  // Yellow
        COLOR_TO_FACE.put('G', F);  // Green
        COLOR_TO_FACE.put('B', B);  // Blue
        COLOR_TO_FACE.put('O', L);  // Orange
        COLOR_TO_FACE.put('R', R);  // Red
    }

    // Corner positions: each corner has 3 stickers, each with [face, row, col]
    static final int[][][] CORNER_POSITIONS = {
            // URF, UFL, ULB, UBR, DFR, DLF, DBL, DRB
            {{U,0,2}, {R,0,0}, {F,0,2}},  // URF (0)
            {{U,0,0}, {F,0,0}, {L,0,2}},  // UFL (1)
            {{U,2,0}, {L,0,0}, {B,0,2}},  // ULB (2)
            {{U,2,2}, {B,0,0}, {R,0,2}},  // UBR (3)
            {{D,0,2}, {F,2,2}, {R,2,0}},  // DFR (4)
            {{D,0,0}, {L,2,2}, {F,2,0}},  // DLF (5)
            {{D,2,0}, {B,2,2}, {L,2,0}},  // DBL (6)
            {{D,2,2}, {R,2,2}, {B,2,0}}   // DRB (7)
    };

    // Edge positions: each edge has 2 stickers
    static final int[][][] EDGE_POSITIONS = {
            // UR, UF, UL, UB, DR, DF, DL, DB, FR, FL, BL, BR
            {{U,0,1}, {R,0,1}},  // UR (0)
            {{U,1,0}, {F,0,1}},  // UF (1)
            {{U,1,2}, {L,0,1}},  // UL (2)
            {{U,2,1}, {B,0,1}},  // UB (3)
            {{D,0,1}, {R,2,1}},  // DR (4)
            {{D,1,0}, {F,2,1}},  // DF (5)
            {{D,1,2}, {L,2,1}},  // DL (6)
            {{D,2,1}, {B,2,1}},  // DB (7)
            {{F,1,2}, {R,1,0}},  // FR (8)
            {{F,1,0}, {L,1,2}},  // FL (9)
            {{B,1,0}, {L,1,0}},  // BL (10)
            {{B,1,2}, {R,1,2}}   // BR (11)
    };

    // Corner solved state: what corner should be at each position
    static final int[] SOLVED_CP = {0, 1, 2, 3, 4, 5, 6, 7};

    // Edge solved state
    static final int[] SOLVED_EP = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11};

    // Move definitions for compact representation
    static final int[][] CORNER_MOVE_TABLE = new int[18][8];
    static final int[][] CORNER_ORIENTATION_MOVE_TABLE = new int[18][8];
    static final int[][] EDGE_MOVE_TABLE = new int[18][12];
    static final int[][] EDGE_ORIENTATION_MOVE_TABLE = new int[18][12];

    // Phase 1 moves: U, D, R2, L2, F2, B2 (indices)
    static final int[] PHASE1_MOVES = {0, 1, 2, 3, 4, 5, 14, 15, 16, 17}; // U, U', U2, D, D', D2, L2, R2, F2, B2

    // Phase 2 moves: G1 moves (all except quarter turns of F and B)
    static final int[] PHASE2_MOVES = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17}; // All moves

    // All 18 possible moves
    static final String[] MOVE_NAMES = {
            "U", "U'", "U2",
            "D", "D'", "D2",
            "F", "F'", "F2",
            "B", "B'", "B2",
            "L", "L'", "L2",
            "R", "R'", "R2"
    };

    static long startTime;
    static final long TIME_LIMIT = 20_000; // 20 seconds

    // Pruning tables - make them public so CompactCube can access them
    public static byte[] phase1CornerTable;
    public static byte[] phase1EdgeTable;
    public static byte[] phase2CornerTable;
    public static byte[] phase2EdgeTable;

    // ============================
    // INITIALIZE MOVE TABLES
    // ============================
    static {
        initializeMoveTables();
    }

    static void initializeMoveTables() {
        // Initialize all move tables
        for (int move = 0; move < 18; move++) {
            // Create solved cube
            CompactCube cube = new CompactCube(); // Use default constructor for solved cube

            // Apply move using 3D
            cube.applyMoveUsing3D(move);

            // Record how corners moved
            for (int corner = 0; corner < 8; corner++) {
                // Find where this corner went
                for (int target = 0; target < 8; target++) {
                    if (cube.cp[target] == corner) {
                        CORNER_MOVE_TABLE[move][corner] = target;
                        CORNER_ORIENTATION_MOVE_TABLE[move][corner] = cube.co[target];
                        break;
                    }
                }
            }

            // Record how edges moved
            for (int edge = 0; edge < 12; edge++) {
                for (int target = 0; target < 12; target++) {
                    if (cube.ep[target] == edge) {
                        EDGE_MOVE_TABLE[move][edge] = target;
                        EDGE_ORIENTATION_MOVE_TABLE[move][edge] = cube.eo[target];
                        break;
                    }
                }
            }
        }
    }

    // ============================
    // MAIN
    // ============================
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("usage: java rubikscube.Solver input output");
            return;
        }

        // Load or generate pruning tables
        System.out.print("Loading pruning tables... ");
        loadPruningTables();
        System.out.println("Done");

        char[][][] cube = parseCubeFromFile(args[0]);

        startTime = System.currentTimeMillis();
        String solution = solveTwoPhase(cube);

        System.out.println("Time: " + (System.currentTimeMillis() - startTime) + "ms");
        System.out.println("Solution: " + solution);
        writeSolution(args[1], solution.trim());
    }

    // ============================
    // PRUNING TABLE MANAGEMENT
    // ============================
    static void loadPruningTables() {
        try {
            File corner1File = new File("phase1_corner.dat");
            File edge1File = new File("phase1_edge.dat");
            File corner2File = new File("phase2_corner.dat");
            File edge2File = new File("phase2_edge.dat");

            if (corner1File.exists() && edge1File.exists() &&
                    corner2File.exists() && edge2File.exists()) {

                phase1CornerTable = Files.readAllBytes(corner1File.toPath());
                phase1EdgeTable = Files.readAllBytes(edge1File.toPath());
                phase2CornerTable = Files.readAllBytes(corner2File.toPath());
                phase2EdgeTable = Files.readAllBytes(edge2File.toPath());
                return;
            }
        } catch (Exception e) {
            System.out.println("Error loading tables: " + e.getMessage());
        }

        // Generate tables
        System.out.println("\nGenerating pruning tables...");
        generatePruningTables();
        savePruningTables();
    }

    static void generatePruningTables() {
        System.out.print("Generating phase1 corner table... ");
        phase1CornerTable = generatePhase1CornerTable();
        System.out.println("Done");

        System.out.print("Generating phase1 edge table... ");
        phase1EdgeTable = generatePhase1EdgeTable();
        System.out.println("Done");

        System.out.print("Generating phase2 corner table... ");
        phase2CornerTable = generatePhase2CornerTable();
        System.out.println("Done");

        System.out.print("Generating phase2 edge table... ");
        phase2EdgeTable = generatePhase2EdgeTable();
        System.out.println("Done");
    }

    static byte[] generatePhase1CornerTable() {
        byte[] table = new byte[2187]; // 3^7
        Arrays.fill(table, (byte)99);

        Queue<Integer> queue = new LinkedList<>();
        table[0] = 0;
        queue.add(0);

        while (!queue.isEmpty()) {
            int state = queue.poll();
            int distance = table[state] & 0xFF;

            for (int move : PHASE1_MOVES) {
                int nextState = applyCornerOrientMove(state, move);
                if (table[nextState] == 99) {
                    table[nextState] = (byte)(distance + 1);
                    queue.add(nextState);
                }
            }
        }
        return table;
    }

    static int applyCornerOrientMove(int state, int move) {
        // Convert state to corner orientations
        int[] co = new int[8];
        int temp = state;
        for (int i = 6; i >= 0; i--) {
            co[i] = temp % 3;
            temp /= 3;
        }
        co[7] = (3 - (co[0] + co[1] + co[2] + co[3] + co[4] + co[5] + co[6]) % 3) % 3;

        // Apply move
        int[] newCO = new int[8];
        for (int corner = 0; corner < 8; corner++) {
            int target = CORNER_MOVE_TABLE[move][corner];
            newCO[target] = (co[corner] + CORNER_ORIENTATION_MOVE_TABLE[move][corner]) % 3;
        }

        // Convert back to index
        int newState = 0;
        for (int i = 0; i < 7; i++) {
            newState = newState * 3 + newCO[i];
        }
        return newState;
    }

    static byte[] generatePhase1EdgeTable() {
        byte[] table = new byte[2048]; // 2^11
        Arrays.fill(table, (byte)99);

        Queue<Integer> queue = new LinkedList<>();
        table[0] = 0;
        queue.add(0);

        while (!queue.isEmpty()) {
            int state = queue.poll();
            int distance = table[state] & 0xFF;

            for (int move : PHASE1_MOVES) {
                int nextState = applyEdgeOrientMove(state, move);
                if (table[nextState] == 99) {
                    table[nextState] = (byte)(distance + 1);
                    queue.add(nextState);
                }
            }
        }
        return table;
    }

    static int applyEdgeOrientMove(int state, int move) {
        int[] eo = new int[12];
        int temp = state;
        for (int i = 10; i >= 0; i--) {
            eo[i] = temp % 2;
            temp /= 2;
        }
        eo[11] = (eo[0] ^ eo[1] ^ eo[2] ^ eo[3] ^ eo[4] ^ eo[5] ^
                eo[6] ^ eo[7] ^ eo[8] ^ eo[9] ^ eo[10]) % 2;

        int[] newEO = new int[12];
        for (int edge = 0; edge < 12; edge++) {
            int target = EDGE_MOVE_TABLE[move][edge];
            newEO[target] = (eo[edge] + EDGE_ORIENTATION_MOVE_TABLE[move][edge]) % 2;
        }

        int newState = 0;
        for (int i = 0; i < 11; i++) {
            newState = newState * 2 + newEO[i];
        }
        return newState;
    }

    static byte[] generatePhase2CornerTable() {
        byte[] table = new byte[40320]; // 8!
        Arrays.fill(table, (byte)99);

        Queue<Integer> queue = new LinkedList<>();
        table[0] = 0;
        queue.add(0);

        while (!queue.isEmpty()) {
            int state = queue.poll();
            int distance = table[state] & 0xFF;

            for (int move : PHASE2_MOVES) {
                int nextState = applyCornerPermMove(state, move);
                if (table[nextState] == 99) {
                    table[nextState] = (byte)(distance + 1);
                    queue.add(nextState);
                }
            }
        }
        return table;
    }

    static int applyCornerPermMove(int state, int move) {
        int[] cp = decodeLehmer(state, 8);
        int[] newCP = new int[8];

        for (int c = 0; c < 8; c++) {
            int dest = CORNER_MOVE_TABLE[move][c];
            newCP[dest] = cp[c];
        }

        return getLehmerCode(newCP, 8);
    }

    static byte[] generatePhase2EdgeTable() {
        byte[] table = new byte[40320]; // 8! for UD edges
        Arrays.fill(table, (byte)99);

        Queue<Integer> queue = new LinkedList<>();
        table[0] = 0;
        queue.add(0);

        while (!queue.isEmpty()) {
            int state = queue.poll();
            int distance = table[state] & 0xFF;

            for (int move : PHASE2_MOVES) {
                int nextState = applyEdgePermMove(state, move);
                if (table[nextState] == 99) {
                    table[nextState] = (byte)(distance + 1);
                    queue.add(nextState);
                }
            }
        }
        return table;
    }

    static int applyEdgePermMove(int state, int move) {
        int[] ep = decodeLehmer(state, 8);
        int[] newEP = new int[8];

        for (int e = 0; e < 8; e++) {
            int dest = EDGE_MOVE_TABLE_UD[move][e];
            newEP[dest] = ep[e];
        }

        return getLehmerCode(newEP, 8);
    }

    static final int[][] EDGE_MOVE_TABLE_UD = buildUdEdgeMoveTable();

    static int[][] buildUdEdgeMoveTable() {
        int[][] tbl = new int[18][8];

        // Initialize with identity
        for (int m = 0; m < 18; m++) {
            for (int i = 0; i < 8; i++) {
                tbl[m][i] = i;
            }
        }

        // U moves (affects edges 0-3: UR, UF, UL, UB)
        apply4Cycle(tbl, 0, new int[]{1, 0, 3, 2}); // U
        apply4Cycle(tbl, 1, new int[]{2, 3, 0, 1}); // U'
        apply4Cycle(tbl, 2, new int[]{3, 2, 1, 0}); // U2

        // D moves (affects edges 4-7: DR, DF, DL, DB)
        apply4Cycle(tbl, 3, new int[]{5, 4, 7, 6}); // D
        apply4Cycle(tbl, 4, new int[]{6, 7, 4, 5}); // D'
        apply4Cycle(tbl, 5, new int[]{7, 6, 5, 4}); // D2

        // F2, B2, L2, R2 affect edges in the middle slice
        // For simplicity, we'll leave them as identity for now
        // This can be refined later

        return tbl;
    }

    static void apply4Cycle(int[][] tbl, int move, int[] cycle) {
        int temp = tbl[move][cycle[0]];
        tbl[move][cycle[0]] = tbl[move][cycle[1]];
        tbl[move][cycle[1]] = tbl[move][cycle[2]];
        tbl[move][cycle[2]] = tbl[move][cycle[3]];
        tbl[move][cycle[3]] = temp;
    }

    static void savePruningTables() {
        try {
            Files.write(Paths.get("phase1_corner.dat"), phase1CornerTable);
            Files.write(Paths.get("phase1_edge.dat"), phase1EdgeTable);
            Files.write(Paths.get("phase2_corner.dat"), phase2CornerTable);
            Files.write(Paths.get("phase2_edge.dat"), phase2EdgeTable);
            System.out.println("Tables saved to disk");
        } catch (Exception e) {
            System.out.println("Error saving tables: " + e.getMessage());
        }
    }

    // ============================
    // TWO-PHASE SOLVER
    // ============================
    static String solveTwoPhase(char[][][] startCube) {
        // Convert to compact representation
        CompactCube state = new CompactCube(startCube);

        System.out.println("Initial state heuristic: P1=" + state.phase1Heuristic() +
                ", P2=" + state.phase2Heuristic());

        // Phase 1: Solve orientations
        System.out.print("Phase 1... ");
        String phase1Solution = idaSearch(state, true);
        if (phase1Solution == null) {
            System.out.println("Failed!");
            return "No solution found";
        }
        System.out.println("Found (" + phase1Solution.split(" ").length + " moves)");

        // Apply phase 1 solution to get to G1 state
        for (String move : phase1Solution.split(" ")) {
            if (!move.isEmpty()) {
                state.applyMove(getMoveIndex(move));
            }
        }

        // Phase 2: Solve permutations
        System.out.print("Phase 2... ");
        String phase2Solution = idaSearch(state, false);
        if (phase2Solution == null) {
            System.out.println("Failed!");
            return "No solution found";
        }
        System.out.println("Found (" + phase2Solution.split(" ").length + " moves)");

        String fullSolution = (phase1Solution + " " + phase2Solution).trim();
        System.out.println("Total moves: " + fullSolution.split(" ").length);
        return fullSolution;
    }

    // ============================
    // IDA* SEARCH
    // ============================
    static String idaSearch(CompactCube startState, boolean phase1) {
        int bound = phase1 ? startState.phase1Heuristic() : startState.phase2Heuristic();

        System.out.print("Initial bound: " + bound + " ");

        for (int depth = bound; depth <= (phase1 ? 12 : 18); depth++) {
            System.out.print(depth + " ");
            Node result = search(startState, 0, depth, "", phase1, new HashSet<>());
            if (result != null) {
                System.out.println();
                return result.path;
            }
            if (System.currentTimeMillis() - startTime > TIME_LIMIT) {
                System.out.println("\nTimeout!");
                return null;
            }
        }
        System.out.println("\nMax depth reached!");
        return null;
    }

    static Node search(CompactCube state, int g, int bound, String path,
                       boolean phase1, Set<Long> visited) {
        int h = phase1 ? state.phase1Heuristic() : state.phase2Heuristic();
        int f = g + h;

        if (f > bound) return null;
        if (h == 0) return new Node(path, g);

        int[] moves = phase1 ? PHASE1_MOVES : PHASE2_MOVES;

        for (int move : moves) {
            // Prune redundant moves
            if (!path.isEmpty()) {
                String[] pathMoves = path.trim().split(" ");
                int lastMove = getMoveIndex(pathMoves[pathMoves.length - 1]);
                if (isRedundant(lastMove, move)) continue;
            }

            CompactCube nextState = state.copy();
            nextState.applyMove(move);

            // Check visited
            long hash = nextState.hashCode();
            if (visited.contains(hash)) continue;
            visited.add(hash);

            Node result = search(nextState, g + 1, bound,
                    path + MOVE_NAMES[move] + " ", phase1, visited);
            if (result != null) return result;

            if (System.currentTimeMillis() - startTime > TIME_LIMIT) {
                return null;
            }
        }
        return null;
    }

    static int getMoveIndex(String moveName) {
        for (int i = 0; i < MOVE_NAMES.length; i++) {
            if (MOVE_NAMES[i].equals(moveName)) return i;
        }
        return -1;
    }

    static boolean isRedundant(int lastMove, int currentMove) {
        if (lastMove < 0 || currentMove < 0) return false;
        int lastFace = lastMove / 3;
        int currentFace = currentMove / 3;

        // Don't do same face moves consecutively
        return lastFace == currentFace;
    }

    // ============================
    // LEHMER CODE UTILITIES
    // ============================
    static int getLehmerCode(int[] perm, int n) {
        int code = 0;
        for (int i = 0; i < n; i++) {
            int smaller = 0;
            for (int j = i + 1; j < n; j++) {
                if (perm[j] < perm[i]) smaller++;
            }
            code = code * (n - i) + smaller;
        }
        return code;
    }

    static int[] decodeLehmer(int code, int n) {
        int[] perm = new int[n];
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < n; i++) numbers.add(i);

        for (int i = 0; i < n; i++) {
            int fact = factorial(n - 1 - i);
            int digit = code / fact;
            code = code % fact;

            perm[i] = numbers.get(digit);
            numbers.remove(digit);
        }
        return perm;
    }

    static int factorial(int n) {
        int result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }

    // ============================
    // HELPER CLASSES
    // ============================
    static class Node {
        String path;
        int cost;

        Node(String path, int cost) {
            this.path = path;
            this.cost = cost;
        }
    }

    // ============================
    // ORIGINAL METHODS (KEEP THESE)
    // ============================
    public static char[][][] parseCubeFromFile(String file) throws Exception {
        char[][][] c = new char[6][3][3];
        List<String> raw = Files.readAllLines(Paths.get(file));
        if (raw.size() < 9)
            throw new Exception("File must have 9 lines");

        String[] line = new String[9];
        for (int i = 0; i < 9; i++)
            line[i] = raw.get(i).replaceAll("[^A-Za-z]", "");

        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 3; col++)
                c[0][r][col] = line[r].charAt(col);

        for (int r = 0; r < 3; r++) {
            String mid = line[r + 3];
            if (mid.length() != 12)
                throw new Exception("Middle row must have 12 letters: " + mid);

            c[4][r] = mid.substring(0, 3).toCharArray();
            c[2][r] = mid.substring(3, 6).toCharArray();
            c[5][r] = mid.substring(6, 9).toCharArray();
            c[3][r] = mid.substring(9,12).toCharArray();
        }

        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 3; col++)
                c[1][r][col] = line[r + 6].charAt(col);

        return c;
    }

    static void writeSolution(String file, String sol) {
        try (FileWriter fw = new FileWriter(file)) {
            fw.write(sol);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Your existing move application methods
    static char[][][] applyMove3D(char[][][] c, String m) {
        switch (m) {
            case "U":  return rotateU(c);
            case "U'": return rotateUPrime(c);
            case "U2": return rotateUTwice(c);
            case "D":  return rotateD(c);
            case "D'": return rotateDPrime(c);
            case "D2": return rotateDTwice(c);
            case "F":  return rotateF(c);
            case "F'": return rotateFPrime(c);
            case "F2": return rotateFTwice(c);
            case "B":  return rotateB(c);
            case "B'": return rotateBPrime(c);
            case "B2": return rotateBTwice(c);
            case "L":  return rotateL(c);
            case "L'": return rotateLPrime(c);
            case "L2": return rotateLTwice(c);
            case "R":  return rotateR(c);
            case "R'": return rotateRPrime(c);
            case "R2": return rotateRTwice(c);
        }
        return copyCube(c);
    }

    static char[][][] copyCube(char[][][] c) {
        return CubeState.copyCube(c); // Use existing CubeState method
    }

    // Rotation methods (using CubeState.copyCube)
    static void rotateFaceCW(char[][] face) {
        char[][] tmp = new char[3][3];
        for (int r = 0; r < 3; r++)
            for (int col = 0; col < 3; col++)
                tmp[col][2 - r] = face[r][col];
        for (int r = 0; r < 3; r++)
            System.arraycopy(tmp[r], 0, face[r], 0, 3);
    }

    static char[][][] rotateU(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[0]);
        char[] temp = c[2][0].clone();
        c[2][0] = c[5][0].clone();
        c[5][0] = c[3][0].clone();
        c[3][0] = c[4][0].clone();
        c[4][0] = temp;
        return c;
    }

    static char[][][] rotateD(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[1]);
        char[] temp = c[2][2].clone();
        c[2][2] = c[4][2].clone();
        c[4][2] = c[3][2].clone();
        c[3][2] = c[5][2].clone();
        c[5][2] = temp;
        return c;
    }

    static char[][][] rotateL(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[4]);
        char[] temp = { c[0][0][0], c[0][1][0], c[0][2][0] };
        for (int i = 0; i < 3; i++) c[0][i][0] = c[3][2-i][2];
        for (int i = 0; i < 3; i++) c[3][i][2] = c[1][2-i][0];
        for (int i = 0; i < 3; i++) c[1][i][0] = c[2][i][0];
        for (int i = 0; i < 3; i++) c[2][i][0] = temp[i];
        return c;
    }

    static char[][][] rotateR(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[5]);
        char[] temp = { c[0][0][2], c[0][1][2], c[0][2][2] };
        for (int i = 0; i < 3; i++) c[0][i][2] = c[2][i][2];
        for (int i = 0; i < 3; i++) c[2][i][2] = c[1][i][2];
        for (int i = 0; i < 3; i++) c[1][i][2] = c[3][2-i][0];
        for (int i = 0; i < 3; i++) c[3][i][0] = temp[2-i];
        return c;
    }

    static char[][][] rotateF(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[2]);
        char[] temp = c[0][2].clone();
        for (int i = 0; i < 3; i++) c[0][2][i] = c[4][2-i][2];
        for (int i = 0; i < 3; i++) c[4][i][2] = c[1][0][i];
        for (int i = 0; i < 3; i++) c[1][0][i] = c[5][i][0];
        for (int i = 0; i < 3; i++) c[5][i][0] = temp[i];
        return c;
    }

    static char[][][] rotateB(char[][][] cube) {
        char[][][] c = CubeState.copyCube(cube);
        rotateFaceCW(c[3]);
        char[] temp = c[0][0].clone();
        for (int i = 0; i < 3; i++) c[0][0][i] = c[5][i][2];
        for (int i = 0; i < 3; i++) c[5][i][2] = c[1][2][2-i];
        for (int i = 0; i < 3; i++) c[1][2][i] = c[4][i][0];
        for (int i = 0; i < 3; i++) c[4][i][0] = temp[2-i];
        return c;
    }

    static char[][][] rotateUPrime(char[][][] c) {
        return rotateU(rotateU(rotateU(c)));
    }
    static char[][][] rotateUTwice(char[][][] c) {
        return rotateU(rotateU(c));
    }

    static char[][][] rotateDPrime(char[][][] c) {
        return rotateD(rotateD(rotateD(c)));
    }
    static char[][][] rotateDTwice(char[][][] c) {
        return rotateD(rotateD(c));
    }

    static char[][][] rotateLPrime(char[][][] c) {
        return rotateL(rotateL(rotateL(c)));
    }
    static char[][][] rotateLTwice(char[][][] c) {
        return rotateL(rotateL(c));
    }

    static char[][][] rotateRPrime(char[][][] c) {
        return rotateR(rotateR(rotateR(c)));
    }
    static char[][][] rotateRTwice(char[][][] c) {
        return rotateR(rotateR(c));
    }

    static char[][][] rotateFPrime(char[][][] c) {
        return rotateF(rotateF(rotateF(c)));
    }
    static char[][][] rotateFTwice(char[][][] c) {
        return rotateF(rotateF(c));
    }

    static char[][][] rotateBPrime(char[][][] c) {
        return rotateB(rotateB(rotateB(c)));
    }
    static char[][][] rotateBTwice(char[][][] c) {
        return rotateB(rotateB(c));
    }

    // ============================
    // CONVERT TO ALLOWED MOVES
    // ============================
    static String convertToAllowedMoves(String solution) {
        StringBuilder out = new StringBuilder();
        for (String m : solution.split(" ")) {
            switch (m) {
                case "U": out.append("U "); break;
                case "U'": out.append("U U U "); break;
                case "U2": out.append("U U "); break;
                case "D": out.append("D "); break;
                case "D'": out.append("D D D "); break;
                case "D2": out.append("D D "); break;
                case "F": out.append("F "); break;
                case "F'": out.append("F F F "); break;
                case "F2": out.append("F F "); break;
                case "B": out.append("B "); break;
                case "B'": out.append("B B B "); break;
                case "B2": out.append("B B "); break;
                case "L": out.append("L "); break;
                case "L'": out.append("L L L "); break;
                case "L2": out.append("L L "); break;
                case "R": out.append("R "); break;
                case "R'": out.append("R R R "); break;
                case "R2": out.append("R R "); break;
            }
        }
        return out.toString().trim();
    }
}