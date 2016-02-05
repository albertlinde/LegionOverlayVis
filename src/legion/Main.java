package legion;

import edu.uci.ics.jung.algorithms.layout.CircleLayout;
import edu.uci.ics.jung.algorithms.layout.ISOMLayout;
import edu.uci.ics.jung.algorithms.layout.KKLayout;
import edu.uci.ics.jung.algorithms.layout.Layout;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.visualization.VisualizationViewer;
import edu.uci.ics.jung.visualization.control.DefaultModalGraphMouse;
import edu.uci.ics.jung.visualization.control.ModalGraphMouse;
import edu.uci.ics.jung.visualization.decorators.EdgeShape;
import edu.uci.ics.jung.visualization.decorators.ToStringLabeller;
import edu.uci.ics.jung.visualization.renderers.Renderer;
import org.apache.commons.collections15.Transformer;

import javax.swing.*;
import javax.swing.border.Border;
import java.awt.*;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        final Map<String, List<String>> params = new HashMap<String, List<String>>();

        List<String> options = null;
        for (final String a : args) {
            if (a.charAt(0) == '-') {
                if (a.length() < 2) {
                    System.err.println("Error at argument " + a);
                    return;
                }
                options = new ArrayList<String>();
                params.put(a.substring(1), options);
            } else if (options != null) {
                options.add(a);
            } else {
                System.err.println("Illegal parameter usage");
                return;
            }
        }

        if (!(params.containsKey("o") && params.containsKey("v") && params.containsKey("f"))) {
            printUsage();
            return;
        }
        String overlayType = params.get("o").get(0);
        String visualizationType = params.get("v").get(0);
        if (!(overlayType.equals("circle") || overlayType.equals("isom") || overlayType.equals("kk"))) {
            printUsage();
            return;
        }
        if (!(visualizationType.equals("final") || visualizationType.equals("interval"))) {
            printUsage();
            return;
        }

        File f = new File(params.get("f").get(0));
        if (!f.exists()) {
            System.err.println("FILE DOESN'T EXIST.");
            printUsage();
            return;
        }
        System.out.println(f.getAbsoluteFile());

        try {
            parseFile(f, overlayType, visualizationType);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            printUsage();
        }
    }


    private static void printUsage() {
        System.err.println("Wrong parameters.");
        System.err.println("Usage:");
        System.err.println("java -jar java.jar -o overlay -v visualization -f file");
        System.err.println("overlay: (circle,isom,kk)");
        System.err.println("visualization: (final,interval)");
        System.err.println("example using run.sh: $./run.sh -o isom -v final -f overlay.txt");

    }


    private static void parseFile(File f, String overlayType, String visualizationType) throws FileNotFoundException {
        Scanner s = new Scanner(f);

        Set<String> data = new TreeSet<String>();
        while (s.hasNextLine()) {
            String line = s.nextLine();
            if ((line.contains("OPEN") || line.contains("CLOSE")))
                data.add(line);
        }

        final Graph<String, String> g = new SparseGraph<String, String>();
        g.addVertex("localhost:8002");
        g.addVertex("localhost:8004");

        Layout<String, String> layout = null;
        if (overlayType.equals("isom")) {
            layout = new ISOMLayout<String, String>(g);
        } else if (overlayType.equals("kk")) {
            layout = new KKLayout<String, String>(g);
        } else if (overlayType.equals("circle")) {
            layout = new CircleLayout<String, String>(g);
        } else {
            System.err.println("No overlay type defined.");
            return;
        }

        Transformer<String, Paint> vertexPaint = new Transformer<String, Paint>() {
            public Paint transform(String i) {
                Set<String> neighbours = new TreeSet<String>();

                if (i.contains("localhost"))
                    return Color.red;

                neighbours.addAll(g.getPredecessors(i));
                neighbours.addAll(g.getSuccessors(i));
                for (String s : neighbours) {
                    if (s.contains("8002")) {
                        return Color.BLUE;
                    }
                    if (s.contains("8004")) {
                        return Color.orange;
                    }
                }
                return Color.green;
            }
        };


        if (visualizationType.equals("interval")) {

            layout.setSize(new Dimension(800, 800));
            layout.setGraph(g);

            VisualizationViewer<String, String> vv = new VisualizationViewer<String, String>(layout);
            vv.setPreferredSize(new Dimension(1000, 800));
            vv.getRenderContext().setEdgeShapeTransformer(new EdgeShape.Line<String, String>());


            vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);

            vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller());


            vv.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.CNTR);
            vv.setBackground(Color.white);

            DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
            gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
            vv.setGraphMouse(gm);

            JFrame frame = new JFrame("Nodes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel contentPanel = new JPanel();
            contentPanel.setBackground(Color.white);
            Border padding = BorderFactory.createEmptyBorder(5, 5, 5, 5);

            contentPanel.setBorder(padding);

            frame.setContentPane(contentPanel);


            frame.getContentPane().add(vv);
            frame.pack();
            frame.setVisible(true);

            long last = 0;
            for (String data_point : data) {
                try {
                    if (!(data_point.contains("OPEN") || data_point.contains("CLOSE"))) continue;
                    Long time = Long.valueOf(data_point.split(" ")[0]);
                    if (last == 0)
                        last = time;
                    else {
                        System.out.println("SLEEP: " + (time - last));
                        while (time - last > 3000) {
                            System.out.println("Skipping some time.");
                            last += 2000;
                        }
                        Thread.sleep((time - last));
                        last = time;
                    }

                    vv.updateUI();
                    parse(data_point, g);
                    vv.updateUI();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        } else if (visualizationType.equals("final")) {
            for (String data_point : data) {
                try {
                    if (!(data_point.contains("OPEN") || data_point.contains("CLOSE"))) continue;
                    parse(data_point, g);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            layout.setSize(new Dimension(800, 800));
            layout.setGraph(g);


            VisualizationViewer<String, String> vv = new VisualizationViewer<String, String>(layout);
            vv.setPreferredSize(new Dimension(1000, 800));
            vv.getRenderContext().setEdgeShapeTransformer(new EdgeShape.Line<String, String>());

            vv.getRenderContext().setVertexFillPaintTransformer(vertexPaint);
            vv.getRenderContext().setVertexLabelTransformer(new ToStringLabeller());

            vv.getRenderer().getVertexLabelRenderer().setPosition(Renderer.VertexLabel.Position.CNTR);
            vv.setBackground(Color.white);

            DefaultModalGraphMouse gm = new DefaultModalGraphMouse();
            gm.setMode(ModalGraphMouse.Mode.TRANSFORMING);
            vv.setGraphMouse(gm);

            JFrame frame = new JFrame("Nodes");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

            JPanel contentPanel = new JPanel();
            contentPanel.setBackground(Color.white);
            Border padding = BorderFactory.createEmptyBorder(5, 5, 5, 5);

            contentPanel.setBorder(padding);

            frame.setContentPane(contentPanel);

            frame.getContentPane().add(vv);
            frame.pack();
            frame.setVisible(true);
        }


        Map<Integer, Integer> degreeCounter = new HashMap<Integer, Integer>();
        for (String vertex : g.getVertices()) {
            Integer amount = 0;
            int degree = g.degree(vertex);
            if (degreeCounter.containsKey(degree))
                amount = degreeCounter.get(degree);
            amount++;
            degreeCounter.put(degree, amount);
        }

        System.out.println("Vertices: " + g.getVertexCount());
        System.out.println("Edges: " + g.getEdgeCount());
        for (Integer degree : degreeCounter.keySet()) {
            System.out.println("Degree: " + degree + ": " + degreeCounter.get(degree));
        }
    }

    private static void parse(String s, Graph<String, String> g) {
        if (s.contains("Overlay")) {
            parse(s.substring(s.split(" ")[0].length()).trim(), g);
            return;
        }
        System.out.println(s);
        String[] line = s.split(" ");

        if (line[0].equals("OPEN")) {
            if (!g.containsVertex(line[1]))
                g.addVertex(line[1]);
            if (!g.containsVertex(line[3]))
                g.addVertex(line[3]);
            g.addEdge(line[1] + "-" + line[3], line[1], line[3]);
            g.addEdge(line[3] + "-" + line[1], line[3], line[1]);
        }
        if (line[0].equals("CLOSE")) {
            g.removeEdge(line[1] + "-" + line[3]);
            g.removeEdge(line[3] + "-" + line[1]);
        }
    }
}
