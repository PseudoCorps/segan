package data;

import core.crossvalidation.CrossValidation;
import core.crossvalidation.Fold;
import core.crossvalidation.Instance;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.util.ArrayList;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.Options;
import util.CLIUtils;
import util.IOUtils;
import util.StatisticsUtils;
import util.normalizer.ZNormalizer;

/**
 *
 * @author vietan
 */
public class ResponseTextDataset extends TextDataset {

    protected double[] responses;

    public ResponseTextDataset(String name) {
        super(name);
    }

    public ResponseTextDataset(String name, String folder) {
        super(name, folder);
    }

    public ResponseTextDataset(String name, String folder,
            CorpusProcessor corpProc) {
        super(name, folder, corpProc);
    }

    public double[] getResponses() {
        return this.responses;
    }

    public void setResponses(double[] responses) {
        this.responses = responses;
    }

    public void setResponses(ArrayList<Double> res) {
        this.responses = new double[res.size()];
        for (int ii = 0; ii < res.size(); ii++) {
            this.responses[ii] = res.get(ii);
        }
    }

    public double[] getResponses(ArrayList<Integer> instances) {
        double[] res = new double[instances.size()];
        for (int i = 0; i < res.length; i++) {
            int idx = instances.get(i);
            res[i] = responses[idx];
        }
        return res;
    }

    public ZNormalizer zNormalize() {
        ZNormalizer znorm = new ZNormalizer(responses);
        for (int ii = 0; ii < responses.length; ii++) {
            responses[ii] = znorm.normalize(responses[ii]);
        }
        return znorm;
    }

    public void loadResponses(String responseFilepath) throws Exception {
        if (verbose) {
            logln("--- Loading response from file " + responseFilepath);
        }

        if (this.docIdList == null) {
            throw new RuntimeException("docIdList is null. Load text data first.");
        }

        this.responses = new double[this.docIdList.size()];
        String line;
        BufferedReader reader = IOUtils.getBufferedReader(responseFilepath);
        while ((line = reader.readLine()) != null) {
            String[] sline = line.split("\t");
            String docId = sline[0];
            double docResponse = Double.parseDouble(sline[1]);
            int index = this.docIdList.indexOf(docId);
            this.responses[index] = docResponse;
        }
        reader.close();
    }

    @Override
    protected void outputDocumentInfo(String outputFolder) throws Exception {
        File outputFile = new File(outputFolder, formatFilename + docInfoExt);
        if (verbose) {
            logln("--- Outputing document info ... " + outputFile);
        }

        BufferedWriter infoWriter = IOUtils.getBufferedWriter(outputFile);
        for (int docIndex : this.processedDocIndices) {
            infoWriter.write(this.docIdList.get(docIndex)
                    + "\t" + this.responses[docIndex]
                    + "\n");
        }
        infoWriter.close();
    }

    @Override
    public void inputDocumentInfo(File filepath) throws Exception {
        if (verbose) {
            logln("--- Reading document info from " + filepath);
        }

        BufferedReader reader = IOUtils.getBufferedReader(filepath);
        String line;
        String[] sline;
        docIdList = new ArrayList<String>();
        ArrayList<Double> responseList = new ArrayList<Double>();

        while ((line = reader.readLine()) != null) {
            sline = line.split("\t");
            docIdList.add(sline[0]);
            responseList.add(Double.parseDouble(sline[1]));
        }
        reader.close();

        this.docIds = docIdList.toArray(new String[docIdList.size()]);
        this.responses = new double[responseList.size()];
        for (int i = 0; i < this.responses.length; i++) {
            this.responses[i] = responseList.get(i);
        }
    }

    /**
     * Create cross validation
     *
     * @param cvFolder Cross validation folder
     * @param numFolds Number of folds
     * @param trToDevRatio Ratio between the number of training and the number
     * of test data
     * @param numClasses Number of discrete classes for stratified sampling
     */
    public void createCrossValidation(String cvFolder, int numFolds, double trToDevRatio, int numClasses)
            throws Exception {
        ArrayList<Instance<String>> instanceList = new ArrayList<Instance<String>>();
        ArrayList<Integer> groupIdList = StatisticsUtils.discretize(responses, numClasses);
        for (int d = 0; d < this.docIdList.size(); d++) {
            instanceList.add(new Instance<String>(docIdList.get(d)));
        }

        String cvName = "";
        CrossValidation<String, Instance<String>> cv =
                new CrossValidation<String, Instance<String>>(
                cvFolder,
                cvName,
                instanceList);

        cv.stratify(groupIdList, numFolds, trToDevRatio);
        cv.outputFolds();

        for (Fold<String, Instance<String>> fold : cv.getFolds()) {
            // processor
            CorpusProcessor cp = new CorpusProcessor(corpProc);

            // training data
            ResponseTextDataset trainData = new ResponseTextDataset(fold.getFoldName(),
                    cv.getFolderPath(), cp);
            trainData.setFormatFilename(fold.getFoldName() + Fold.TrainingExt);
            ArrayList<String> trDocIds = new ArrayList<String>();
            ArrayList<String> trDocTexts = new ArrayList<String>();
            double[] trResponses = new double[fold.getNumTrainingInstances()];
            for (int ii = 0; ii < fold.getNumTrainingInstances(); ii++) {
                int idx = fold.getTrainingInstances().get(ii);
                trDocIds.add(this.docIdList.get(idx));
                trDocTexts.add(this.textList.get(idx));
                trResponses[ii] = responses[idx];
            }
            trainData.setTextData(trDocIds, trDocTexts);
            trainData.setResponses(trResponses);
            trainData.format(fold.getFoldFolderPath());

            // development data: process using vocab from training
            ResponseTextDataset devData = new ResponseTextDataset(fold.getFoldName(),
                    cv.getFolderPath(), cp);
            devData.setFormatFilename(fold.getFoldName() + Fold.DevelopExt);
            ArrayList<String> deDocIds = new ArrayList<String>();
            ArrayList<String> deDocTexts = new ArrayList<String>();
            double[] deResponses = new double[fold.getNumDevelopmentInstances()];
            for (int ii = 0; ii < fold.getNumDevelopmentInstances(); ii++) {
                int idx = fold.getDevelopmentInstances().get(ii);
                deDocIds.add(this.docIdList.get(idx));
                deDocTexts.add(this.textList.get(idx));
                deResponses[ii] = responses[idx];
            }
            devData.setTextData(deDocIds, deDocTexts);
            devData.setResponses(deResponses);
            devData.format(fold.getFoldFolderPath());

            // test data: process using vocab from training
            ResponseTextDataset testData = new ResponseTextDataset(fold.getFoldName(),
                    cv.getFolderPath(), cp);
            testData.setFormatFilename(fold.getFoldName() + Fold.TestExt);
            ArrayList<String> teDocIds = new ArrayList<String>();
            ArrayList<String> teDocTexts = new ArrayList<String>();
            double[] teResponses = new double[fold.getNumTestingInstances()];
            for (int ii = 0; ii < fold.getNumTestingInstances(); ii++) {
                int idx = fold.getTestingInstances().get(ii);
                teDocIds.add(this.docIdList.get(idx));
                teDocTexts.add(this.textList.get(idx));
                teResponses[ii] = responses[idx];
            }
            testData.setTextData(teDocIds, teDocTexts);
            testData.setResponses(teResponses);
            testData.format(fold.getFoldFolderPath());
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("# docs: ").append(docIds.length).append("\n");
        double max = StatisticsUtils.max(responses);
        double min = StatisticsUtils.min(responses);
        double mean = StatisticsUtils.mean(responses);
        double stdv = StatisticsUtils.standardDeviation(responses);
        int[] bins = StatisticsUtils.bin(responses, 5);
        str.append("range: ").append(min).append(" - ").append(max).append("\n");
        str.append("mean: ").append(mean).append(". stdv: ").append(stdv).append("\n");
        for (int ii = 0; ii < bins.length; ii++) {
            str.append(ii).append("\t").append(bins[ii]).append("\n");
        }
        return str.toString();
    }

    public static String getHelpString() {
        return "java -cp 'dist/segan.jar:dist/lib/*' " + ResponseTextDataset.class.getName() + " -help";
    }

    public static void main(String[] args) {
        try {
            parser = new BasicParser();

            // create the Options
            options = new Options();

            // directories
            addOption("dataset", "Dataset");
            addOption("data-folder", "Folder that stores the processed data");
            addOption("text-data", "Directory of the text data");
            addOption("format-folder", "Folder that stores formatted data");
            addOption("format-file", "Formatted file name");
            addOption("response-file", "Directory of the response file");
            addOption("word-voc-file", "Directory of the word vocab file (if any)");

            // text processing
            addOption("u", "The minimum count of raw unigrams");
            addOption("b", "The minimum count of raw bigrams");
            addOption("bs", "The minimum score of bigrams");
            addOption("V", "Maximum vocab size");
            addOption("min-tf", "Term frequency minimum cutoff");
            addOption("max-tf", "Term frequency maximum cutoff");
            addOption("min-df", "Document frequency minimum cutoff");
            addOption("max-df", "Document frequency maximum cutoff");
            addOption("min-doc-length", "Document minimum length");
            addOption("min-word-length", "Word minimum length");

            // cross validation
            addOption("num-folds", "Number of folds. Default 5.");
            addOption("tr2dev-ratio", "Training-to-development ratio. Default 0.8.");
            addOption("cv-folder", "Folder to store cross validation folds");
            addOption("num-classes", "Number of classes that the response");

            addOption("run-mode", "Run mode");

            options.addOption("v", false, "Verbose");
            options.addOption("d", false, "Debug");
            options.addOption("s", false, "Whether stopwords are filtered");
            options.addOption("l", false, "Whether lemmatization is performed");
            options.addOption("file", false, "Whether the text input data is stored in a file or a folder");
            options.addOption("help", false, "Help");

            cmd = parser.parse(options, args);
            if (cmd.hasOption("help")) {
                CLIUtils.printHelp(getHelpString(), options);
                return;
            }

            verbose = cmd.hasOption("v");
            debug = cmd.hasOption("d");

            String runMode = cmd.getOptionValue("run-mode");
            if (runMode.equals("process")) {
                process(args);
            } else if (runMode.equals("load")) {
                load(args);
            } else if (runMode.equals("cross-validation")) {
                crossValidate(args);
            } else {
                throw new RuntimeException("Run mode " + runMode + " is not supported");
            }

        } catch (Exception e) {
            e.printStackTrace();
            CLIUtils.printHelp(getHelpString(), options);
            System.exit(1);
        }
    }

    public static void crossValidate(String[] args) throws Exception {
        String datasetName = cmd.getOptionValue("dataset");
        String datasetFolder = cmd.getOptionValue("data-folder");
        String textInputData = cmd.getOptionValue("text-data");
        String responseFile = cmd.getOptionValue("response-file");

        int unigramCountCutoff = CLIUtils.getIntegerArgument(cmd, "u", 0);
        int bigramCountCutoff = CLIUtils.getIntegerArgument(cmd, "b", 0);
        double bigramScoreCutoff = CLIUtils.getDoubleArgument(cmd, "bs", 5.0);
        int maxVocabSize = CLIUtils.getIntegerArgument(cmd, "V", Integer.MAX_VALUE);
        int vocTermFreqMinCutoff = CLIUtils.getIntegerArgument(cmd, "min-tf", 0);
        int vocTermFreqMaxCutoff = CLIUtils.getIntegerArgument(cmd, "max-tf", Integer.MAX_VALUE);
        int vocDocFreqMinCutoff = CLIUtils.getIntegerArgument(cmd, "min-df", 0);
        int vocDocFreqMaxCutoff = CLIUtils.getIntegerArgument(cmd, "max-df", Integer.MAX_VALUE);
        int docTypeCountCutoff = CLIUtils.getIntegerArgument(cmd, "min-doc-length", 1);
        int minWordLength = CLIUtils.getIntegerArgument(cmd, "min-word-length", 1);

        boolean stopwordFilter = cmd.hasOption("s");
        boolean lemmatization = cmd.hasOption("l");

        int numFolds = CLIUtils.getIntegerArgument(cmd, "num-folds", 5);
        double trToDevRatio = CLIUtils.getDoubleArgument(cmd, "tr2dev-ratio", 0.8);
        String cvFolder = cmd.getOptionValue("cv-folder");
        int numClasses = CLIUtils.getIntegerArgument(cmd, "num-classes", 1);
        IOUtils.createFolder(cvFolder);

        CorpusProcessor corpProc = new CorpusProcessor(
                unigramCountCutoff,
                bigramCountCutoff,
                bigramScoreCutoff,
                maxVocabSize,
                vocTermFreqMinCutoff,
                vocTermFreqMaxCutoff,
                vocDocFreqMinCutoff,
                vocDocFreqMaxCutoff,
                docTypeCountCutoff,
                minWordLength,
                stopwordFilter,
                lemmatization);

        ResponseTextDataset dataset = new ResponseTextDataset(datasetName, datasetFolder, corpProc);
        // load text data
        if (cmd.hasOption("file")) {
            dataset.loadTextDataFromFile(textInputData);
        } else {
            dataset.loadTextDataFromFolder(textInputData);
        }
        dataset.loadResponses(responseFile); // load response data
        dataset.createCrossValidation(cvFolder, numFolds, trToDevRatio, numClasses);
    }

    public static ResponseTextDataset load(String[] args) throws Exception {
        String datasetName = cmd.getOptionValue("dataset");
        String datasetFolder = cmd.getOptionValue("data-folder");
        String formatFolder = cmd.getOptionValue("format-folder");
        String formatFile = CLIUtils.getStringArgument(cmd, "format-file", datasetName);

        ResponseTextDataset data = new ResponseTextDataset(datasetName, datasetFolder);
        data.setFormatFilename(formatFile);
        data.loadFormattedData(new File(data.getDatasetFolderPath(), formatFolder));
        return data;
    }

    /**
     * Load train/development/test data in a cross validation fold.
     *
     * @param fold The given fold
     */
    public static ResponseTextDataset[] loadCrossValidationFold(Fold fold) throws Exception {
        ResponseTextDataset[] foldData = new ResponseTextDataset[3];
        ResponseTextDataset trainData = new ResponseTextDataset(fold.getFoldName(), fold.getFolder());
        trainData.setFormatFilename(fold.getFoldName() + Fold.TrainingExt);
        trainData.loadFormattedData(fold.getFoldFolderPath());
        foldData[Fold.TRAIN] = trainData;

        ResponseTextDataset devData = new ResponseTextDataset(fold.getFoldName(), fold.getFolder());
        devData.setFormatFilename(fold.getFoldName() + Fold.DevelopExt);
        devData.loadFormattedData(fold.getFoldFolderPath());
        foldData[Fold.DEV] = devData;

        ResponseTextDataset testData = new ResponseTextDataset(fold.getFoldName(), fold.getFolder());
        testData.setFormatFilename(fold.getFoldName() + Fold.TestExt);
        testData.loadFormattedData(fold.getFoldFolderPath());
        foldData[Fold.TEST] = testData;

        return foldData;
    }

    public static void process(String[] args) throws Exception {
        String datasetName = cmd.getOptionValue("dataset");
        String datasetFolder = cmd.getOptionValue("data-folder");
        String textInputData = cmd.getOptionValue("text-data");
        String formatFolder = cmd.getOptionValue("format-folder");
        String formatFile = CLIUtils.getStringArgument(cmd, "format-file", datasetName);
        String responseFile = cmd.getOptionValue("response-file");

        int unigramCountCutoff = CLIUtils.getIntegerArgument(cmd, "u", 0);
        int bigramCountCutoff = CLIUtils.getIntegerArgument(cmd, "b", 0);
        double bigramScoreCutoff = CLIUtils.getDoubleArgument(cmd, "bs", 5.0);
        int maxVocabSize = CLIUtils.getIntegerArgument(cmd, "V", Integer.MAX_VALUE);
        int vocTermFreqMinCutoff = CLIUtils.getIntegerArgument(cmd, "min-tf", 0);
        int vocTermFreqMaxCutoff = CLIUtils.getIntegerArgument(cmd, "max-tf", Integer.MAX_VALUE);
        int vocDocFreqMinCutoff = CLIUtils.getIntegerArgument(cmd, "min-df", 0);
        int vocDocFreqMaxCutoff = CLIUtils.getIntegerArgument(cmd, "max-df", Integer.MAX_VALUE);
        int docTypeCountCutoff = CLIUtils.getIntegerArgument(cmd, "min-doc-length", 1);

        boolean stopwordFilter = cmd.hasOption("s");
        boolean lemmatization = cmd.hasOption("l");

        CorpusProcessor corpProc = new CorpusProcessor(
                unigramCountCutoff,
                bigramCountCutoff,
                bigramScoreCutoff,
                maxVocabSize,
                vocTermFreqMinCutoff,
                vocTermFreqMaxCutoff,
                vocDocFreqMinCutoff,
                vocDocFreqMaxCutoff,
                docTypeCountCutoff,
                stopwordFilter,
                lemmatization);

        // If the word vocab file is given, use it. This is usually for the case
        // where training data have been processed and now test data are processed
        // using the word vocab from the training data.
        if (cmd.hasOption("word-voc-file")) {
            String wordVocFile = cmd.getOptionValue("word-voc-file");
            corpProc.loadVocab(wordVocFile);
        }

        ResponseTextDataset dataset = new ResponseTextDataset(
                datasetName, datasetFolder, corpProc);
        dataset.setFormatFilename(formatFile);

        // load text data
        if (cmd.hasOption("file")) {
            dataset.loadTextDataFromFile(textInputData);
        } else {
            dataset.loadTextDataFromFolder(textInputData);
        }
        dataset.loadResponses(responseFile); // load response data
        dataset.format(new File(dataset.getDatasetFolderPath(), formatFolder));
    }

    /**
     * Z-normalize the response variable using the training responses as the
     * base distribution.
     *
     * @param train The training data
     * @param dev The development data
     * @param test The test data
     */
    public static void zNormalize(
            ResponseTextDataset train,
            ResponseTextDataset dev,
            ResponseTextDataset test) {
        ZNormalizer zNorm = new ZNormalizer(train.getResponses());

        // train
        double[] zNormTrResponse = new double[train.responses.length];
        for (int ii = 0; ii < zNormTrResponse.length; ii++) {
            zNormTrResponse[ii] = zNorm.normalize(train.responses[ii]);
        }
        train.setResponses(zNormTrResponse);

        // development
        if (dev != null) {
            double[] zNormDeResponse = new double[dev.responses.length];
            for (int ii = 0; ii < zNormDeResponse.length; ii++) {
                zNormDeResponse[ii] = zNorm.normalize(dev.responses[ii]);
            }
            dev.setResponses(zNormDeResponse);
        }

        // test
        if (test != null) {
            double[] zNormTeResponse = new double[test.responses.length];
            for (int ii = 0; ii < zNormTeResponse.length; ii++) {
                zNormTeResponse[ii] = zNorm.normalize(test.responses[ii]);
            }
            test.setResponses(zNormTeResponse);
        }
    }

    public static double[][] zNormalize(
            double[] trResponses,
            double[] deResponses,
            double[] teResponses) {
        double[][] normResponses = new double[3][];
        ZNormalizer zNorm = new ZNormalizer(trResponses);
        // train
        double[] zNormTrResponse = new double[trResponses.length];
        for (int ii = 0; ii < zNormTrResponse.length; ii++) {
            zNormTrResponse[ii] = zNorm.normalize(trResponses[ii]);
        }
        normResponses[Fold.TRAIN] = zNormTrResponse;

        // dev
        if (deResponses != null) {
            double[] zNormDeResponse = new double[deResponses.length];
            for (int ii = 0; ii < zNormDeResponse.length; ii++) {
                zNormDeResponse[ii] = zNorm.normalize(deResponses[ii]);
            }
            normResponses[Fold.DEV] = zNormDeResponse;
        }

        // test
        if (teResponses != null) {
            double[] zNormTeResponse = new double[teResponses.length];
            for (int ii = 0; ii < zNormTeResponse.length; ii++) {
                zNormTeResponse[ii] = zNorm.normalize(teResponses[ii]);
            }
            normResponses[Fold.TEST] = zNormTeResponse;
        }
        return normResponses;
    }
}
