package nanook;

import java.io.BufferedReader;
import java.io.*;
import java.util.*;

/**
 * Represents a read set (Template reads, Complement reads, or 2D reads).
 * 
 * @author Richard Leggett
 */
public class ReadSet {
    private NanoOKOptions options;
    private AlignmentFileParser parser;
    private ReadSetStats stats;
    private References references;
    private int type;
    private int nFastaFiles=0;
    private String typeString;
   
    /**
     * Constructor
     * @param t type (defined in NanoOKOprions)
     * @param o NanoOKOptions object
     * @param r the References
     * @param p an alignment parser object
     * @param s set of stats to associate with this read set
     */
    public ReadSet(int t, NanoOKOptions o, References r, AlignmentFileParser p, ReadSetStats s) {
        options = o;
        parser = p;
        references = r;
        type = t;
        stats = s;
    }
        
    /**
     * Parse a FASTA or FASTQ file, noting length of reads etc.
     * @param filename filename of FASTA file
     */
    private void readQueryFile(String filename) {
        SequenceReader sr = new SequenceReader(false);
        int nReadsInFile;
        
        if (options.getReadFormat() == NanoOKOptions.FASTQ) {
            nReadsInFile = sr.indexFASTQFile(filename);
        } else {
            nReadsInFile = sr.indexFASTAFile(filename, null, true);
        }

        if (nReadsInFile > 1) {
            System.out.println("Warning: File "+filename+" has more than 1 read.");
        }

        for (int i=0; i<sr.getSequenceCount(); i++) {
            String id = sr.getID(i);
            
            if (id.startsWith("00000000-0000-0000-0000-000000000000")) {
                System.out.println("Error:");
                System.out.println(filename);
                System.out.println("The reads in this file do not have unique IDs because they were generated when MinKNOW was producing UUIDs, but Metrichor was not using them. To fix, run nanook_extract_reads with the -fixids option.");
                System.exit(1);
            }
            
            stats.addLength(id, sr.getLength(i));
        }
    }

    /**
     * Check if filename has valid read extension 
     * @param f flename
     * @return true if valid for chosen aligner
     */
    private boolean isValidReadExtension(String f) {
        boolean r = false;
        
        if (options.getReadFormat() == NanoOKOptions.FASTQ) {
            if ((f.endsWith(".fastq")) || (f.endsWith(".fq"))) {
                r = true;
            }
        } else {
            if ((f.endsWith(".fasta")) || (f.endsWith(".fa"))) {
                r = true;
            }
        }
        
        return r;
    }
    
    /**
     * Gather length statistics on all files in this read set.
     */
    public int processReads() {
        String dirs[] = new String[2];
        int readTypes[] = new int[2];
        int maxReads = options.getMaxReads();
        int nDirs = 0;
        
        nFastaFiles=0;

        typeString = options.getTypeFromInt(type);
                
        stats.openLengthsFile();

        if (options.isNewStyleDir()) {
            if (options.isProcessingPassReads()) {
                dirs[nDirs] = options.getReadDir() + File.separator + "pass";
                readTypes[nDirs] = NanoOKOptions.READTYPE_PASS;
                nDirs++;
            }
            
            if (options.isProcessingFailReads()) {
                dirs[nDirs] = options.getReadDir() + File.separator + "fail";
                readTypes[nDirs] = NanoOKOptions.READTYPE_FAIL;
                nDirs++;
            }
        } else {
            dirs[nDirs] = options.getReadDir();
            readTypes[nDirs] = NanoOKOptions.READTYPE_COMBINED;
            nDirs++;
        }
                
        for (int dirIndex=0; dirIndex<nDirs; dirIndex++) {        
            String inputDir = dirs[dirIndex] + File.separator + options.getTypeFromInt(type);
            File folder = new File(inputDir);
            File[] listOfFiles = folder.listFiles();

            if (listOfFiles == null) {
                System.out.println("Directory "+inputDir+" doesn't exist");
            } else if (listOfFiles.length <= 0) {
                System.out.println("Directory "+inputDir+" empty");
            } else {
                System.out.println("");
                System.out.println("Gathering stats from "+inputDir);
            
                for (File file : listOfFiles) {
                    if (file.isFile()) {
                        if (isValidReadExtension(file.getName())) {
                            readQueryFile(file.getPath());
                            stats.addReadFile(dirIndex, readTypes[dirIndex]);
                            nFastaFiles++;

                            if ((nFastaFiles % 100) == 0) {
                                System.out.print("\r"+nFastaFiles);
                            }


                            if ((maxReads > 0) && (nFastaFiles >= maxReads)) {
                                 break;
                            }
                        }
                    }
                }

                System.out.println("\r"+nFastaFiles);
            }
        }
        
        stats.closeLengthsFile();
             
        //System.out.println("Calculating...");
        stats.calculateStats();    
        
        return nFastaFiles;
    }
        
    /**
     * Pick top alignment from sorted list. List is sorted in order of score, but if there are
     * matching scores, we pick one at random.
     * @param al list of alignments
     * @return index
     */
    private int pickTopAlignment(List<Alignment> al) {
        int index = 0;
        int topScore = al.get(0).getScore();
        int countSame = 0;
        
        //for (int i=0; i<al.size(); i++) {
        //    System.out.println(i+" = "+al.get(i).getScore());
        //}
        
        // Find out how many have the same score
        while ((countSame < al.size()) && (al.get(countSame).getScore() == topScore)) {
            countSame++;
        }
        
        if (countSame > 1) {
            Random rn = new Random();
            index = rn.nextInt(countSame);
        }
        
        //System.out.println("Index chosen ("+countSame+") "+index);
        
        return index;
    }
    
    /**
     * Parse all alignment files for this read set.
     * Code in common with gatherLengthStats - combine?
     */
    public int processAlignments() {
        int nReads = 0;
        int nReadsWithAlignments = 0;
        int nReadsWithoutAlignments = 0;
        String dirs[] = new String[2];
        int readTypes[] = new int[2];
        int nDirs = 0;
        int maxReads = options.getMaxReads();
        String outputFilename = options.getAnalysisDir() + File.separator + "Unaligned" + File.separator + options.getTypeFromInt(type) + "_nonaligned.txt";
        AlignmentsTableFile nonAlignedSummary = new AlignmentsTableFile(outputFilename);
        
        if (options.isNewStyleDir()) {
            if (options.isProcessingPassReads()) {
                dirs[nDirs] = options.getAlignerDir() + File.separator + "pass";
                readTypes[nDirs] = NanoOKOptions.READTYPE_PASS;
                nDirs++;
            }
            
            if (options.isProcessingFailReads()) {
                dirs[nDirs] = options.getAlignerDir() + File.separator + "fail";
                readTypes[nDirs] = NanoOKOptions.READTYPE_FAIL;
                nDirs++;
            }
        } else {
            dirs[nDirs] = options.getAlignerDir();
            readTypes[nDirs] = NanoOKOptions.READTYPE_COMBINED;
            nDirs++;
        }
        
        for (int dirIndex=0; dirIndex<nDirs; dirIndex++) {        
            String inputDir = dirs[dirIndex] + File.separator + options.getTypeFromInt(type);
            File folder = new File(inputDir);
            File[] listOfFiles = folder.listFiles();
            
            if (listOfFiles == null) {
                System.out.println("Directory "+inputDir+" doesn't exist");
            } else if (listOfFiles.length <= 0) {
                System.out.println("Directory "+inputDir+" empty");
            } else {            
                System.out.println("Parsing from " + inputDir);            
                for (File file : listOfFiles) {
                    if (file.isFile()) {
                        if (file.getName().endsWith(parser.getAlignmentFileExtension())) {
                            String pathname = inputDir + File.separator + file.getName();
                            int nAlignments;

                            options.getLog().println("");
                            options.getLog().println("> New file " + file.getName());
                            options.getLog().println("");
                            
                            nAlignments = parser.parseFile(pathname, nonAlignedSummary, stats);

                            if (nAlignments > 0) {
                                nReadsWithAlignments++;
                                parser.sortAlignments();
                                List<Alignment> al = parser.getHighestScoringSet();
                                int topAlignment = pickTopAlignment(al);
                                String readReferenceName = al.get(topAlignment).getHitName();
                                
                                options.getLog().println("Query size = " + al.get(topAlignment).getQuerySequenceSize());
                                options.getLog().println("  Hit size = " + al.get(topAlignment).getHitSequenceSize());
                                
                                ReferenceSequence readReference = references.getReferenceById(readReferenceName);
                                AlignmentMerger merger = new AlignmentMerger(options, readReference, al.get(topAlignment).getQuerySequenceSize(), stats, type);
                                for (int i=topAlignment; i<al.size(); i++) {
                                    Alignment a = al.get(i);
                                    merger.addAlignment(a);
                                }
                                AlignmentInfo stat = merger.endMergeAndStoreStats();
                                readReference.getStatsByType(type).getAlignmentsTableFile().writeMergedAlignment(file.getName(), merger, stat);  
                            } else {
                                nReadsWithoutAlignments++;
                            }

                            nReads++;
                            if ((nReads % 100) == 0) {
                                System.out.print("\r"+nReads+"/"+nFastaFiles);
                            }

                            if ((maxReads > 0) && (nReads >= maxReads)) {
                                break;
                            }
                        }
                    }
                }
                System.out.println("\r" + nFastaFiles + "/" + nFastaFiles + " ("+(nFastaFiles - nReads)+")");
            }
        }

        stats.writeSummaryFile(options.getAlignmentSummaryFilename());
        
        return nReadsWithAlignments;
    }
    
    /**
     * Get type of this read set.
     * @return a String (e.g. "Template")
     */
    public String getTypeString() {
        return typeString;
    }
    
    /**
     * Get stats object.
     * @return a ReadSetStats object
     */
    public ReadSetStats getStats() {
        return stats;
    }
}
