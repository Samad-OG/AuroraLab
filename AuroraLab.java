import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * AuroraLab - an interactive science & art studio written in pure Java (Swing).
 *
 *  1. Galaxy Forge    : real-time N-body gravity simulation with black holes
 *  2. Fractal Voyager : parallel Mandelbrot/Julia explorer with animated palettes
 *  3. Life Lab        : Conway's Game of Life on a wrapping grid
 *
 * Compile:  javac AuroraLab.java
 * Run:      java AuroraLab
 */
public class AuroraLab extends JFrame {

    private final JTabbedPane tabs = new JTabbedPane();

    public AuroraLab() {
        super("AuroraLab - Interactive Science & Art Studio");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1150, 760);
        setLocationRelativeTo(null);

        tabs.addTab("Galaxy Forge", page(new Galaxy()));
        tabs.addTab("Fractal Voyager", page(new Fractal()));
        tabs.addTab("Life Lab", page(new Life()));
        add(tabs);

        JMenuBar mb = new JMenuBar();
        JMenu file = new JMenu("File");
        JMenuItem shot = new JMenuItem("Save Screenshot (PNG)");
        shot.addActionListener(e -> screenshot());
        JMenuItem exit = new JMenuItem("Exit");
        exit.addActionListener(e -> System.exit(0));
        file.add(shot);
        file.addSeparator();
        file.add(exit);
        mb.add(file);
        setJMenuBar(mb);
    }

    /** Each tab is a view (center) + a control bar (north). Views implement HasControls. */
    private static JPanel page(JPanel view) {
        JPanel p = new JPanel(new BorderLayout());
        p.add(((HasControls) view).controls(), BorderLayout.NORTH);
        p.add(view, BorderLayout.CENTER);
        return p;
    }

    private void screenshot() {
        Component c = tabs.getSelectedComponent();
        BufferedImage im = new BufferedImage(c.getWidth(), c.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = im.createGraphics();
        c.paint(g);
        g.dispose();
        File f = new File(System.getProperty("user.home"), "AuroraLab_" + System.currentTimeMillis() + ".png");
        try {
            ImageIO.write(im, "png", f);
            JOptionPane.showMessageDialog(this, "Saved to:\n" + f.getAbsolutePath());
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(this, "Could not save: " + ex.getMessage());
        }
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> new AuroraLab().setVisible(true));
    }

    // ------------------------------------------------------------------ UI helpers

    interface HasControls { JPanel controls(); }

    static JPanel bar(JComponent... cs) {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 6));
        p.setBackground(new Color(18, 18, 28));
        for (JComponent c : cs) p.add(c);
        return p;
    }

    static JButton btn(String text, Runnable r) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.addActionListener(e -> r.run());
        return b;
    }

    static JCheckBox chk(String text, boolean sel, Consumer<Boolean> c) {
        JCheckBox b = new JCheckBox(text, sel);
        b.setForeground(Color.WHITE);
        b.setOpaque(false);
        b.setFocusable(false);
        b.addItemListener(e -> c.accept(b.isSelected()));
        return b;
    }

    static JLabel lbl(String t) {
        JLabel l = new JLabel(t);
        l.setForeground(new Color(200, 200, 220));
        return l;
    }

    static void hud(Graphics g, int x, int y, String... lines) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        g.setColor(new Color(255, 255, 255, 170));
        for (String s : lines) { g.drawString(s, x, y); y += 17; }
    }

    // ================================================================== 1. GALAXY FORGE

    static class Galaxy extends JPanel implements HasControls {
        static final int MAX = 1300;
        static final double G = 1.0, DT = 0.5, SOFT2 = 64;
        final double[] x = new double[MAX], y = new double[MAX], vx = new double[MAX], vy = new double[MAX], m = new double[MAX];
        final boolean[] dead = new boolean[MAX];
        int n = 0;
        boolean paused = false, trails = true;
        BufferedImage buf;
        final Random rnd = new Random();

        Galaxy() {
            setBackground(Color.BLACK);
            addMouseListener(new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    if (SwingUtilities.isRightMouseButton(e)) addBody(e.getX(), e.getY(), 0, 0, 6000);
                    else spawnGalaxy(e.getX(), e.getY(), 220, rnd.nextGaussian() * 0.3, rnd.nextGaussian() * 0.3, rnd.nextBoolean() ? 1 : -1);
                }
            });
            new javax.swing.Timer(16, e -> { if (!paused) step(); repaint(); }).start();
        }

        public JPanel controls() {
            JButton pause = new JButton("Pause");
            pause.setFocusable(false);
            pause.addActionListener(e -> { paused = !paused; pause.setText(paused ? "Resume" : "Pause"); });
            return bar(pause,
                    chk("Light trails", true, v -> trails = v),
                    btn("Collision!", this::collision),
                    btn("Clear", () -> n = 0),
                    lbl("  Left-click: spawn galaxy   |   Right-click: black hole"));
        }

        void collision() {
            int w = getWidth(), h = getHeight();
            n = 0;
            spawnGalaxy(w * 0.3, h * 0.5, 260, 0.9, 0.15, 1);
            spawnGalaxy(w * 0.7, h * 0.5, 260, -0.9, -0.15, 1);
        }

        void addBody(double px, double py, double pvx, double pvy, double mass) {
            if (n >= MAX) return;
            x[n] = px; y[n] = py; vx[n] = pvx; vy[n] = pvy; m[n] = mass; n++;
        }

        void spawnGalaxy(double cx, double cy, int count, double dvx, double dvy, int spin) {
            if (n + count + 1 > MAX) return;
            double M = 1500;
            addBody(cx, cy, dvx, dvy, M);
            for (int i = 0; i < count; i++) {
                double r = 25 + Math.abs(rnd.nextGaussian()) * 70;
                double a = rnd.nextDouble() * Math.PI * 2;
                double acc = G * M * r / Math.pow(r * r + SOFT2, 1.5);
                double v = Math.sqrt(acc * r) * (1 + rnd.nextGaussian() * 0.03);
                addBody(cx + Math.cos(a) * r, cy + Math.sin(a) * r,
                        dvx - Math.sin(a) * v * spin, dvy + Math.cos(a) * v * spin, 1);
            }
        }

        void step() {
            for (int s = 0; s < 2; s++) {
                for (int i = 0; i < n; i++) {
                    double ax = 0, ay = 0;
                    for (int j = 0; j < n; j++) {
                        if (i == j) continue;
                        double dx = x[j] - x[i], dy = y[j] - y[i];
                        double d2 = dx * dx + dy * dy + SOFT2;
                        double inv = m[j] / (d2 * Math.sqrt(d2));
                        ax += dx * inv; ay += dy * inv;
                    }
                    vx[i] += ax * G * DT; vy[i] += ay * G * DT;
                }
                for (int i = 0; i < n; i++) { x[i] += vx[i] * DT; y[i] += vy[i] * DT; }
            }
            // black holes & stars swallow small bodies; far-away bodies are culled
            int w = getWidth(), h = getHeight();
            for (int i = 0; i < n; i++) {
                if (x[i] < -w || x[i] > 2 * w || y[i] < -h || y[i] > 2 * h) dead[i] = true;
                if (m[i] < 1000 || dead[i]) continue;
                double rad = m[i] >= 5000 ? 14 : 7;
                for (int j = 0; j < n; j++) {
                    if (j == i || dead[j] || m[j] >= 1000) continue;
                    double dx = x[j] - x[i], dy = y[j] - y[i];
                    if (dx * dx + dy * dy < rad * rad) { dead[j] = true; if (m[i] >= 5000) m[i] += 2; }
                }
            }
            int k = 0;
            for (int i = 0; i < n; i++) {
                if (!dead[i]) {
                    if (k != i) { x[k] = x[i]; y[k] = y[i]; vx[k] = vx[i]; vy[k] = vy[i]; m[k] = m[i]; }
                    k++;
                }
                dead[i] = false;
            }
            n = k;
        }

        @Override protected void paintComponent(Graphics g0) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            if (buf == null || buf.getWidth() != w || buf.getHeight() != h) {
                buf = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D b = buf.createGraphics();
                b.setColor(Color.BLACK); b.fillRect(0, 0, w, h); b.dispose();
            }
            Graphics2D b = buf.createGraphics();
            b.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            b.setColor(trails ? new Color(0, 0, 0, 28) : Color.BLACK);
            b.fillRect(0, 0, w, h);
            for (int i = 0; i < n; i++) {
                if (m[i] >= 5000) {            // black hole
                    b.setColor(new Color(255, 140, 20, 90)); b.fillOval((int) x[i] - 18, (int) y[i] - 18, 36, 36);
                    b.setColor(new Color(255, 200, 80)); b.drawOval((int) x[i] - 14, (int) y[i] - 14, 28, 28);
                    b.setColor(Color.BLACK); b.fillOval((int) x[i] - 12, (int) y[i] - 12, 24, 24);
                } else if (m[i] >= 1000) {     // star
                    for (int r = 4; r >= 1; r--) {
                        b.setColor(new Color(255, 230, 150, 30 + (4 - r) * 50));
                        b.fillOval((int) x[i] - r * 4, (int) y[i] - r * 4, r * 8, r * 8);
                    }
                } else {                       // dust / planets: colour by speed
                    double sp = Math.hypot(vx[i], vy[i]);
                    b.setColor(Color.getHSBColor((float) (0.62 - Math.min(sp * 0.09, 0.55)), 0.75f, 1f));
                    b.fillRect((int) x[i], (int) y[i], 2, 2);
                }
            }
            b.dispose();
            g0.drawImage(buf, 0, 0, null);
            hud(g0, 14, h - 24, "bodies: " + n + " / " + MAX + (n >= MAX ? "  (limit reached - press Clear)" : ""));
        }
    }

    // ================================================================== 2. FRACTAL VOYAGER

    static class Fractal extends JPanel implements HasControls {
        double cx = -0.5, cy = 0, scale = 3.2;          // view centre + width in complex units
        boolean julia = false, cycle = true;
        double jr = -0.8, ji = 0.156;
        int phase = 0, palette = 0, iw, ih;
        final int[] lut = new int[1024];
        double[] it = new double[0];
        BufferedImage img;

        Fractal() {
            setBackground(Color.BLACK);
            buildLut();
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) {
                    double s = scale / getWidth();
                    if (SwingUtilities.isRightMouseButton(e)) { scale *= 2.5; }
                    else {
                        cx += (e.getX() - getWidth() / 2.0) * s;
                        cy += (e.getY() - getHeight() / 2.0) * s;
                        scale *= 0.4;
                    }
                    recompute();
                }
                @Override public void mouseMoved(MouseEvent e) {
                    if (!julia) return;
                    jr = e.getX() / (double) getWidth() * 3 - 1.5;
                    ji = e.getY() / (double) getHeight() * 2 - 1;
                    recompute();
                }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            addComponentListener(new ComponentAdapter() {
                @Override public void componentResized(ComponentEvent e) { recompute(); }
            });
            new javax.swing.Timer(30, e -> { if (cycle) { phase += 6; repaint(); } }).start();
        }

        public JPanel controls() {
            JComboBox<String> pal = new JComboBox<>(new String[]{"Rainbow", "Fire", "Ocean", "Neon"});
            pal.setFocusable(false);
            pal.addActionListener(e -> { palette = pal.getSelectedIndex(); buildLut(); repaint(); });
            return bar(chk("Julia mode (move mouse!)", false, v -> { julia = v; resetView(); }),
                    chk("Colour cycling", true, v -> cycle = v),
                    lbl("Palette:"), pal,
                    btn("Reset view", this::resetView),
                    lbl("  Left-click: zoom in   |   Right-click: zoom out"));
        }

        void resetView() {
            cx = julia ? 0 : -0.5; cy = 0; scale = 3.2;
            recompute();
        }

        void buildLut() {
            for (int k = 0; k < 1024; k++) {
                double t = k / 1024.0, s = 0.5 + 0.5 * Math.sin(2 * Math.PI * t);
                float h, sat, br;
                switch (palette) {
                    case 1:  h = (float) (0.13 * s); sat = 1f; br = (float) (0.35 + 0.65 * s); break;
                    case 2:  h = (float) (0.5 + 0.15 * (0.5 + 0.5 * Math.cos(2 * Math.PI * t))); sat = 0.8f; br = (float) (0.4 + 0.6 * s); break;
                    case 3:  h = (float) (((0.85 + 0.2 * Math.sin(2 * Math.PI * t)) % 1 + 1) % 1); sat = 1f; br = 1f; break;
                    default: h = (float) t; sat = 0.85f; br = 1f;
                }
                lut[k] = Color.HSBtoRGB(h, sat, br);
            }
        }

        void recompute() {
            int ds = julia ? 2 : 1;                         // Julia renders at half-res for smooth interaction
            int w = Math.max(1, getWidth() / ds), h = Math.max(1, getHeight() / ds);
            if (img == null || iw != w || ih != h) {
                iw = w; ih = h;
                img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                it = new double[w * h];
            }
            final int maxIter = (int) Math.min(2500, 200 + 60 * Math.max(0, Math.log(3.2 / scale) / Math.log(2)));
            final double s = scale / w;
            final double[] out = it;
            final boolean jl = julia;
            final double cR = jr, cI = ji;
            IntStream.range(0, h).parallel().forEach(py -> {
                for (int px = 0; px < w; px++) {
                    double a = cx + (px - w / 2.0) * s, b = cy + (py - h / 2.0) * s;
                    double zr = jl ? a : 0, zi = jl ? b : 0, cr = jl ? cR : a, ci = jl ? cI : b;
                    double zr2 = zr * zr, zi2 = zi * zi;
                    int i = 0;
                    while (zr2 + zi2 <= 256 && i < maxIter) {
                        zi = 2 * zr * zi + ci;
                        zr = zr2 - zi2 + cr;
                        zr2 = zr * zr; zi2 = zi * zi;
                        i++;
                    }
                    out[py * w + px] = i >= maxIter ? -1
                            : i + 1 - Math.log(0.5 * Math.log(zr2 + zi2)) / Math.log(2);
                }
            });
            repaint();
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (img == null) return;
            int[] px = ((DataBufferInt) img.getRaster().getDataBuffer()).getData();
            for (int i = 0; i < px.length && i < it.length; i++) {
                double v = it[i];
                px[i] = v < 0 ? 0 : lut[Math.floorMod((int) (v * 10) + phase, 1024)];
            }
            ((Graphics2D) g).setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img, 0, 0, getWidth(), getHeight(), null);
            hud(g, 14, getHeight() - 40,
                    String.format("zoom: x%.3g", 3.2 / scale),
                    julia ? String.format("Julia c = %.4f %+.4fi", jr, ji) : String.format("centre: %.10f %+.10fi", cx, cy));
        }
    }

    // ================================================================== 3. LIFE LAB

    static class Life extends JPanel implements HasControls {
        static final int C = 150, R = 90;
        int[][] a = new int[R][C];                          // 0 = dead, >0 = age
        int gen = 0, pop = 0;
        final javax.swing.Timer timer;
        final int[] ageColors = new int[100];

        static final String[] GUN = {
                "........................O...........",
                "......................O.O...........",
                "............OO......OO............OO",
                "...........O...O....OO............OO",
                "OO........O.....O...OO..............",
                "OO........O...O.OO....O.O...........",
                "..........O.....O.......O...........",
                "...........O...O....................",
                "............OO......................"};

        Life() {
            setBackground(new Color(8, 8, 14));
            for (int i = 0; i < 100; i++)
                ageColors[i] = Color.HSBtoRGB((float) ((0.5 + i * 0.008) % 1.0), 0.75f, 1f);
            MouseAdapter ma = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent e) { draw(e); }
                @Override public void mouseDragged(MouseEvent e) { draw(e); }
            };
            addMouseListener(ma);
            addMouseMotionListener(ma);
            timer = new javax.swing.Timer(60, e -> { step(); repaint(); });
            timer.start();
            gun();
        }

        public JPanel controls() {
            JButton run = new JButton("Pause");
            run.setFocusable(false);
            run.addActionListener(e -> {
                if (timer.isRunning()) { timer.stop(); run.setText("Run"); }
                else { timer.start(); run.setText("Pause"); }
            });
            JSlider speed = new JSlider(1, 100, 85);
            speed.setOpaque(false);
            speed.addChangeListener(e -> timer.setDelay(Math.max(10, 410 - speed.getValue() * 4)));
            return bar(run, btn("Step", () -> { step(); repaint(); }),
                    btn("Random", this::randomize), btn("Glider Gun", this::gun), btn("Clear", () -> { a = new int[R][C]; gen = 0; repaint(); }),
                    lbl("Speed:"), speed, lbl("  Left-drag: draw   |   Right-drag: erase"));
        }

        void randomize() {
            Random r = new Random();
            for (int i = 0; i < R; i++) for (int j = 0; j < C; j++) a[i][j] = r.nextDouble() < 0.3 ? 1 : 0;
            gen = 0; repaint();
        }

        void gun() {
            a = new int[R][C];
            for (int i = 0; i < GUN.length; i++)
                for (int j = 0; j < GUN[i].length(); j++)
                    if (GUN[i].charAt(j) == 'O') a[i + 8][j + 8] = 1;
            gen = 0; repaint();
        }

        double cs() { return Math.min((double) getWidth() / C, (double) getHeight() / R); }

        void draw(MouseEvent e) {
            double cs = cs();
            double ox = (getWidth() - cs * C) / 2, oy = (getHeight() - cs * R) / 2;
            int c = (int) ((e.getX() - ox) / cs), r = (int) ((e.getY() - oy) / cs);
            if (r < 0 || r >= R || c < 0 || c >= C) return;
            if (SwingUtilities.isRightMouseButton(e)) a[r][c] = 0;
            else if (a[r][c] == 0) a[r][c] = 1;
            repaint();
        }

        void step() {
            int[][] b = new int[R][C];
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++) {
                    int n = 0;
                    for (int dr = -1; dr <= 1; dr++)
                        for (int dc = -1; dc <= 1; dc++)
                            if ((dr != 0 || dc != 0) && a[(r + dr + R) % R][(c + dc + C) % C] > 0) n++;
                    b[r][c] = a[r][c] > 0 ? ((n == 2 || n == 3) ? a[r][c] + 1 : 0) : (n == 3 ? 1 : 0);
                }
            a = b;
            gen++;
        }

        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            double cs = cs();
            double ox = (getWidth() - cs * C) / 2, oy = (getHeight() - cs * R) / 2;
            int size = Math.max(1, (int) Math.ceil(cs) - 1);
            pop = 0;
            for (int r = 0; r < R; r++)
                for (int c = 0; c < C; c++) {
                    if (a[r][c] == 0) continue;
                    pop++;
                    g.setColor(new Color(ageColors[Math.min(a[r][c], 99)]));
                    g.fillRect((int) (ox + c * cs), (int) (oy + r * cs), size, size);
                }
            hud(g, 14, getHeight() - 24, "generation: " + gen + "   population: " + pop);
        }
    }
}
