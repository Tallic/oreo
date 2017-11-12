package uci.mondego;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map.Entry;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Modifier;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.ast.visitor.TreeVisitor;

public class JavaMetricParser {
    public PrintWriter metricsFileWriter = null;
    public PrintWriter bookkeepingWriter = null;
    public PrintWriter tokensFileWriter = null;
    public PrintWriter errorPw = null;
    public String outputDirPath;
    public String metricsFileName;
    public String tokensFileName;
    public String bookkeepingFileName;
    public String errorFileName;
    public static String prefix;
    public static long FILE_COUNTER;
    public static long METHOD_COUNTER;

    public JavaMetricParser(String inputFilePath) {
        this.metricsFileName = "mlcc_input.file";
        this.bookkeepingFileName = "bookkeeping.file";
        this.errorFileName = "error_metrics.file";
        this.tokensFileName= "scc_input.file";
        JavaMetricParser.prefix = this.getBaseName(inputFilePath);
        this.outputDirPath = JavaMetricParser.prefix + "_metric_output";
        File outDir = new File(this.outputDirPath);
        if (!outDir.exists()) {
            outDir.mkdirs();
        }
    }

    private String getBaseName(String path) {
        File inputFile = new File(path);
        String fileName = inputFile.getName();
        int pos = fileName.lastIndexOf(".");
        if (pos > 0) {
            fileName = fileName.substring(0, pos);
        }
        return fileName;
    }

    public static void main(String[] args) throws FileNotFoundException {
        if (args.length > 0) {
            String filename = args[0];
            JavaMetricParser javaMetricParser = new JavaMetricParser(filename);
            BufferedReader br;
            try {
                if (null == javaMetricParser.metricsFileWriter) {
                    javaMetricParser.metricsFileWriter = new PrintWriter(
                            javaMetricParser.outputDirPath + File.separator + javaMetricParser.metricsFileName);
                    javaMetricParser.errorPw = new PrintWriter(
                            javaMetricParser.outputDirPath + File.separator + javaMetricParser.errorFileName);
                    javaMetricParser.bookkeepingWriter = new PrintWriter(
                            javaMetricParser.outputDirPath + File.separator + javaMetricParser.bookkeepingFileName);
                    javaMetricParser.tokensFileWriter = new PrintWriter(
                            javaMetricParser.outputDirPath + File.separator + javaMetricParser.tokensFileName);
                }
                br = new BufferedReader(new InputStreamReader(new FileInputStream(filename), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null && line.trim().length() > 0) {
                    javaMetricParser.calculateMetrics(line.trim());
                }

                try {
                    javaMetricParser.metricsFileWriter.close();
                    javaMetricParser.errorPw.close();
                    javaMetricParser.tokensFileWriter.close();
                    javaMetricParser.bookkeepingWriter.close();
                } catch (Exception e) {
                    System.out.println("Unexpected error while closing the output/error file");
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("FATAL: please specify the file with list of directories!");
        }
        System.out.println("done!");
    }

    public void calculateMetrics(String filename) throws FileNotFoundException {
        System.out.println("processing directory: " + filename);
        List<File> files = DirExplorer.finder(filename);
        for (File f : files) {
            try {
                this.metricalize(f);
            } catch (FileNotFoundException e) {
                System.out.println("WARN: File not found, skipping file: " + f.getAbsolutePath());
                this.errorPw.write(f.getAbsolutePath() + System.lineSeparator());
            } catch (ParseProblemException e) {
                System.out.println("WARN: parse problem exception, skippig file: " + f.getAbsolutePath());
                this.errorPw.write(f.getAbsolutePath() + System.lineSeparator());
                e.printStackTrace();
            } catch (Exception e) {
                System.out.println("WARN: unknown error, skippig file: " + f.getAbsolutePath());
                e.printStackTrace();
            }
        }
    }

    public void metricalize(final File file) throws FileNotFoundException {
        System.out.println("Metricalizing " + file.getName());
        JavaMetricParser.FILE_COUNTER++;
        CompilationUnit cu = JavaParser.parse(file);
        TreeVisitor astVisitor = new TreeVisitor() {

            @Override
            public void process(Node node) {
                if (node instanceof MethodDeclaration || node instanceof ConstructorDeclaration) {
                    JavaMetricParser.METHOD_COUNTER++;
                    // JavaParser.parse(((MethodDeclaration)
                    // node).getBody().get().toString()).get;
                    // System.out.println(((MethodDeclaration)
                    // node).getBody().get().getTokenRange().get().getBegin());
                    MetricCollector collector = new MetricCollector();
                    collector._file = file;
                    // collector.NTOKENS = ((MethodDeclaration)
                    // node).getModifiers().size();
                    for (Modifier c : ((MethodDeclaration) node).getModifiers()) {
                        collector.addToken(c.toString());
                        MapUtils.addOrUpdateMap(collector.removeFromOperandsMap, c.toString());
                        collector.MOD++;
                    }
                    NodeList<ReferenceType> exceptionsThrown = ((MethodDeclaration) node).getThrownExceptions();
                    if (exceptionsThrown.size() > 0) {
                        collector.addToken("throws");
                    }
                    // collector.EXCT = exceptionsThrown.size();
                    NodeList<Parameter> nl = ((MethodDeclaration) node).getParameters();
                    for (Parameter p : nl) {

                        for (Node c : p.getChildNodes()) {
                            if (c instanceof SimpleName)
                                MapUtils.addOrUpdateMap(collector.parameterMap, c.toString());
                        }
                    }
                    collector.NOA = nl.size();
                    collector.START_LINE = node.getBegin().get().line;
                    collector.END_LINE = node.getEnd().get().line;
                    collector._methodName = ((MethodDeclaration) node).getName().asString();
                    MapUtils.addOrUpdateMap(collector.removeFromOperandsMap, collector._methodName);
                    node.accept(new MethodVisitor(), collector);
                    collector.computeHalsteadMetrics();
                    collector.COMP++; // add 1 for the default path.
                    collector.NOS++; // accounting for method declaration
                    collector.innerMethodsMap.remove(collector._methodName);
                    MapUtils.subtractMaps(collector.innerMethodParametersMap, collector.parameterMap);
                    collector.populateVariableRefList();
                    collector.populateMetricHash();
                    String fileId = JavaMetricParser.prefix + JavaMetricParser.FILE_COUNTER;
                    String methodId = fileId + "00" + JavaMetricParser.METHOD_COUNTER;
                    collector.fileId = Long.parseLong(fileId);
                    collector.methodId = Long.parseLong(methodId);
                    this.generateInputForOreo(collector);
                    this.generateInputForScc(collector);
                    // System.out.println(collector._methodName + ", MDN: " +
                    // collector.MDN + ", TDN: " + collector.TDN);
                    // System.out.println(collector);
                }
            }

            public void generateInputForOreo(MetricCollector collector) {
                try {
                    String internalSeparator = ",";
                    String externalSeparator = "@#@";
                    StringBuilder metricString = new StringBuilder("");
                    StringBuilder metadataString = new StringBuilder("");
                    StringBuilder actionTokenString = new StringBuilder("");
                    metadataString.append(collector._file.getParentFile().getName()).append(internalSeparator)
                            .append(collector._file.getName()).append(internalSeparator).append(collector.START_LINE)
                            .append(internalSeparator).append(collector.END_LINE).append(internalSeparator)
                            .append(collector._methodName).append(internalSeparator).append(collector.NTOKENS)
                            .append(internalSeparator).append(collector.tokensMap.size()).append(internalSeparator)
                            .append(collector.metricHash).append(internalSeparator).append(collector.fileId)
                            .append(internalSeparator).append(collector.methodId);
                    metricString.append(collector.COMP).append(internalSeparator).append(collector.NOS)
                            .append(internalSeparator).append(collector.HVOC).append(internalSeparator)
                            .append(collector.HEFF).append(internalSeparator).append(collector.CREF)
                            .append(internalSeparator).append(collector.XMET).append(internalSeparator)
                            .append(collector.LMET).append(internalSeparator).append(collector.NOA)
                            .append(internalSeparator).append(collector.HDIF).append(internalSeparator)
                            .append(collector.VDEC).append(internalSeparator).append(collector.EXCT)
                            .append(internalSeparator).append(collector.EXCR).append(internalSeparator)
                            .append(collector.CAST).append(internalSeparator).append(collector.NAND)
                            .append(internalSeparator).append(collector.VREF).append(internalSeparator)
                            .append(collector.NOPR).append(internalSeparator).append(collector.MDN)
                            .append(internalSeparator).append(collector.NEXP).append(internalSeparator)
                            .append(collector.LOOP);
                    for (Entry<String, Integer> entry : collector.methodCallActionTokensMap.entrySet()) {
                        actionTokenString.append(entry.getKey() + ":" + entry.getValue() + ",");
                    }
                    for (Entry<String, Integer> entry : collector.fieldAccessActionTokensMap.entrySet()) {
                        actionTokenString.append(entry.getKey() + ":" + entry.getValue() + ",");
                    }
                    for (Entry<String, Integer> entry : collector.arrayAccessActionTokensMap.entrySet()) {
                        actionTokenString.append(entry.getKey() + ":" + entry.getValue() + ",");
                    }
                    for (Entry<String, Integer> entry : collector.arrayBinaryAccessActionTokensMap.entrySet()) {
                        actionTokenString.append(entry.getKey() + ":" + entry.getValue() + ",");
                    }
                    actionTokenString.append("_@" + collector._methodName + "@_");
                    StringBuilder line = new StringBuilder("");
                    line.append(metadataString).append(externalSeparator).append(metricString).append(externalSeparator)
                            .append(actionTokenString).append(System.lineSeparator());
                    metricsFileWriter.append(line.toString());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }

            public void generateInputForScc(MetricCollector collector) {
                String internalSeparator = ",";
                String externalSeparator = "@#@";
                StringBuilder metadataString = new StringBuilder("");
                StringBuilder tokenString = new StringBuilder("");

                metadataString.append(collector.fileId).append(internalSeparator).append(collector.methodId)
                        .append(internalSeparator).append(collector.NTOKENS).append(internalSeparator)
                        .append(collector.tokensMap.size());
                for (Entry<String, Integer> entry : collector.tokensMap.entrySet()) {
                    String s = entry.getKey() + "@@::@@" + entry.getValue() + ",";
                    tokenString.append(s);
                }
                tokenString.setLength(tokenString.length() - 1);
                StringBuilder line = new StringBuilder("");
                line.append(metadataString).append(externalSeparator).append(tokenString)
                        .append(System.lineSeparator());

                bookkeepingWriter
                        .println(collector.fileId + ":" + collector._file.getAbsolutePath() + ";" + collector.methodId
                                + ":" + collector._methodName + ";" + collector.START_LINE + ":" + collector.END_LINE);
                tokensFileWriter.append(line);
            }

        };
        astVisitor.visitPreOrder(cu);
    }
}