package nanook;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;

/**
 * Entry class for tool.
 * 
 * @author Richard Leggett
 */
public class NanoOK {
    public final static String VERSION_STRING = "v0.11";
    
    /**
     * Check for program dependencies - R, pdflatex
     */
    public static void checkDependencies() {
        ProcessLogger pl = new ProcessLogger();
        ArrayList<String> response;
        String rVersion = null;
        String pdflatexVersion = null;
                
        response = pl.getCommandOutput("Rscript --version", true, true);
        for (int i=0; i<response.size(); i++) {
            String s = response.get(i);
            if (s.startsWith("R scripting front-end")) {
                rVersion = s;
            }
        }
        
        if (rVersion == null) {
            System.out.println("*** WARNING: Couldn't find Rscript - is R installed? ***");
        } else {
            System.out.println(rVersion);
        }
        
        response = pl.getCommandOutput("pdflatex --version", true, true);
        for (int i=0; i<response.size(); i++) {
            String s = response.get(i);
            if (s.startsWith("pdfTeX")) {
                pdflatexVersion = s;
            }
        }
        
        if (pdflatexVersion == null) {
            System.out.println("*** WARNING: Couldn't find pdflatex - is TeX installed? ***");
        } else {
            System.out.println(pdflatexVersion);
        }
    }

    /**
     * Test logo plotting
     */
    public static void testLogo() {
        SequenceLogo logo = new SequenceLogo();
        logo.drawImage();
        logo.saveImage("/Users/leggettr/Desktop/logo.png");
    }
    
    /**
     * Test SequenceReader class
     */
    public static void testSequenceReader() {
        SequenceReader r = new SequenceReader(true);
        r.indexFASTAFile("/Users/leggettr/Documents/Projects/Nanopore/test.fasta", null, true);
        String s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 0, 499);
        System.out.println("String (0,499) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 0, 9);
        System.out.println("String (0,9) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 200, 209);
        System.out.println("String (200,209) = ["+s+"]");
        s = r.getSubSequence("gi|223667766|ref|NZ_DS264586.1|", 200, 214);
        System.out.println("String (200,214) = ["+s+"]");
    }
    
    /**
     * Test parser
     * @param options
     * @param overallStats
     * @param references 
     */
    public static void testParser(NanoOKOptions options, OverallStats overallStats, References references) {
        AlignmentFileParser p = new LastParser(options, references);
        AlignmentsTableFile nonAlignedSummary = new AlignmentsTableFile("blob.txt");
        //p.parseFile("/Users/leggettr/Documents/Projects/Nanopore/N79681_EvenMC_R7_06082014/last/2D/N79681_EvenMC_R7_0608215_5314_1_ch319_file116_strand.fast5_BaseCalled_2D.fasta.maf", nonAlignedSummary, overallStats);
        //System.exit(0);
    }
    
    /**
     * Get the right parser
     * @param options
     * @return 
     */
    private static AlignmentFileParser getParser(NanoOKOptions options, References references) {
        AlignmentFileParser parser = null;
        
        switch(options.getAligner()) {
            case "last":
                parser = new LastParser(options, references);
                break;
            case "bwa":
                parser = new BWAParser(options, references);                
                break;
            case "blasr":
                parser = new BLASRParser(options, references);                                    
                break;
            case "marginalign":
                parser = new MarginAlignParser(options, references);                                    
                break;
            default:
                System.out.println("Aligner unknown!\n");
                System.exit(1);
                break;                      
        }
        
        return parser;
    }
    
    private static void analyse(NanoOKOptions options) {
        OverallStats overallStats = new OverallStats(options);

        // Load references
        System.out.println("\nFinding references");
        References references = new References(options);
        
        // Parse all reads sets       
        if (options.doParseAlignments()) {
            ReadLengthsSummaryFile summary = new ReadLengthsSummaryFile(options.getLengthSummaryFilename());
            summary.open(options.getSample());
            
            for (int type = 0; type<3; type++) {
                AlignmentFileParser parser = getParser(options, references);  
                options.setReadFormat(parser.getReadFormat());
                ReadSet readSet = new ReadSet(type, options, references, parser, overallStats.getStatsByType(type));
                int nReads = readSet.processReads();

                if (nReads < 1) {
                    System.out.println("Error: unable to find any reads to process.\n");
                    System.exit(1);
                }
                
                int nReadsWithAlignments = readSet.processAlignments();
                if (nReadsWithAlignments < 1) {
                    System.out.println("Error: unable to find any alignments to process.\n");
                    System.exit(1);
                } else if (nReadsWithAlignments < 1000) {
                    System.out.println("Warning: few alignments ("+nReadsWithAlignments+") found to process.\n");                
                }
                                
                summary.addReadSetStats(overallStats.getStatsByType(type));
                overallStats.getStatsByType(type).closeKmersFile();
            }
            summary.close();

            // Write files
            System.out.println("\nWriting analysis files");
            Set<String> ids = references.getAllIds();
            int allCount = ids.size() * 3;
            int counter = 1;            
            for (String id : ids) {
                for (int type=0; type<3; type++) {
                    System.out.print("\r"+counter+"/"+allCount);
                    references.writeReferenceStatFiles(type);
                    references.writeReferenceSummary(type);
                    counter++;
                }
            }
            references.closeAlignmentFiles();
            System.out.println("");
        }
        
        // Plot graphs
        if (options.doPlotGraphs()) {
            System.out.println("\nPlotting graphs");
            RGraphPlotter plotter = new RGraphPlotter(options);
            plotter.plot(references);                
        }
        
        // Make report
        if (options.doMakeReport()) {
            System.out.println("\nMaking report");
            ReportWriter rw = new ReportWriter(options, references, overallStats);
            rw.writeReport();

            if (options.doMakePDF()) {
                System.out.println("\nMaking PDF");
                rw.makePDF();
            }
        }
                
        System.out.println("\nDone");
    }
    
    private static void extract(NanoOKOptions options) {
        ReadExtractor re = new ReadExtractor(options);
        re.createDirectories();
        re.extract();
    }
    
    private static void align(NanoOKOptions options) {
        AlignmentFileParser parser = getParser(options, null);
        ReadAligner aligner = new ReadAligner(options, parser);
        options.setReadFormat(parser.getReadFormat());
        aligner.createDirectories();
        aligner.align();
    }
    
    /**
     * Entry to tool.
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("\nNanoOK " + VERSION_STRING + "\n");
        NanoOKOptions options = new NanoOKOptions();
               
        Locale.setDefault(new Locale("en", "US"));
        
        // Parse command line
        options.parseArgs(args);
        options.checkDirectoryStructure();

        // Check dependencies
        System.out.println("\nChecking dependencies");
        checkDependencies();
        
        if (options.getRunMode() == NanoOKOptions.MODE_EXTRACT) {
            extract(options);
        } else if (options.getRunMode() == NanoOKOptions.MODE_ALIGN) {
            align(options);
        } else if (options.getRunMode() == NanoOKOptions.MODE_ANALYSE) {
            analyse(options);
        }
        
 
    }
}
