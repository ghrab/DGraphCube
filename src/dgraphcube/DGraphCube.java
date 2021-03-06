package dgraphcube;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Scanner;

import materialization.*;

import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.hadoop.fs.Path;

import cuboid.AggregateFunction;
import cuboid.BaseAggregate;
import cuboid.CuboidEntry;
import cuboid.CuboidProcessor;

public class DGraphCube {

	private static Options getOptions(){
		Options options = new Options();
		options.addOption("h","help", false, "Display help");
		options.addOption("dm", "dontMaterialize", false, "Do not apply the materialization strategy");
		options.addOption("inp","inputPath", true, "Input path to graph");
		options.addOption("n", "dimNumber", true, "Number of dimensions");
		options.addOption("vd", "vertexDelimiter", true, "Vertex delimiter");
	    options.addOption("ed", "edgeDelimiter", true, "Edge delimiter");
	    options.addOption("ml", "minLevel", true, "Minimum level to start materializing");
	    options.addOption("k", "limit", true, "Maximum number of cuboid to materialize");
		
		return options;
	}
	
	private static void printHelp(){
		HelpFormatter formatter = new HelpFormatter();
	    formatter.printHelp("DGraphCube", getOptions(), true);
	}
	
	public static void main(String[]args){
		Options options = getOptions();
		CommandLineParser parser = new BasicParser();
		CommandLine cmd = null;
		try{
			cmd = parser.parse(options, args);
		}
		catch(ParseException ex){
			System.err.println(ex.getMessage());
			System.exit(-1);
		}

	    if (args.length == 0 || cmd.hasOption("h")) {
	      printHelp();
	      System.exit(0);
	    }
	    
	    if(!cmd.hasOption("inp")){
	    	System.out.println("Input path required");
	    	printHelp();
	    	System.exit(-1);
	    }
	    
	    if(!cmd.hasOption("n")){
	    	System.out.println("Number of dimensions required");
	    	printHelp();
	    	System.exit(-1);
	    }
	    
	    String [] cuboidArgs = new String[12];
	    cuboidArgs[2] = "-n"; cuboidArgs[3] = cmd.getOptionValue("n");
	    cuboidArgs[4] = "-vd"; cuboidArgs[5] = cmd.getOptionValue("vd", "\t");
	    cuboidArgs[6] = "-ed"; cuboidArgs[7] = cmd.getOptionValue("ed"," ");

	    GraphKeeper graphCube = null;
	    
	    if(cmd.hasOption("dm")){
	    	//Dont materialize... search for possible already materialized aggregated graphs
	    }
	    else{
	    	MaterializationStrategy strategy = new MinLevelStrategy(Integer.parseInt(cmd.getOptionValue("ml")),
	    			Integer.parseInt(cmd.getOptionValue("k")),Integer.parseInt(cmd.getOptionValue("n")),
	    			new Path(cmd.getOptionValue("inp")));
	    	CuboidEntry [] cuboid = new CuboidEntry[2]; //index 0 for cuboid used to compute new cuboid (index 1)
	    	cuboid[0] = null;
	    	cuboid[1] = null;
	    	
	    	while(!strategy.finished(cuboid[0])){
	    		cuboid = strategy.nextAggregate();
	    		cuboidArgs[0] = "-inp"; cuboidArgs[1] = cuboid[0].getPath().toString();
	    		cuboidArgs[8] = "-f"; cuboidArgs[9] = cuboid[1].getAggregateFunction().toString();
	    		cuboidArgs[10] = "-oup"; cuboidArgs[11] = cuboid[1].getPath().toString();
	    		System.out.println("Computing aggregated network : " + cuboid[1].getAggregateFunction().toString());
	    		
	    		try{
	    	    	CuboidProcessor.main(cuboidArgs);
	    	    }
	    	    catch(Exception ex){
	    	    	System.err.println(ex.getMessage());
	    	    	System.exit(-1);
	    	    }
	    		
	    		long size = -1;
	    		try{
	    			File sizeFile = new File("tempSize.txt");
	    			BufferedReader reader = new BufferedReader(new FileReader(sizeFile));
	    			size = Long.parseLong(reader.readLine());
	    			reader.close();
	    			sizeFile.delete();
	    			
	    		}
	    		catch(IOException ex){
	    			System.out.println("Error when reading cuboid size :" + ex.getMessage());
	    		}
	    		System.out.println("size : " + size);
	    		
	    		//modify the size of the cuboid accordingly to the result of the cuboid process
	    		cuboid[0] = cuboid[1];
	    		cuboid[0].setSize(size);
	    	}
	    	graphCube = strategy.getGraphKeeper();
	    }

	    
	    /*
	     * Materialization finished, wait for user input
	     */
	    Scanner in = new Scanner(System.in);
	    System.out.println("Materialization finished\nWaiting for user input");
	    System.out.println("cuboid aggregate fun, bye to quit");
	    while(true){
	    	String next = in.nextLine();
	    	String[] explode = next.split(" ");
	    	if(explode[0].equals("bye")){
	    		in.close();
	    		System.exit(0);
	    	}
	    	else if(explode[0].equals("cuboid")){
	    		if(explode.length != 2) {
	    			System.out.println("Input format error");
	    			continue;
	    		}
	    		/*
	    		 * Should first test if not already on the graphcube
	    		 */
	    		
	    		String funString = explode[1];
	    		AggregateFunction fun = new BaseAggregate();
	    		fun.parseFunction(funString);
	    		
	    		CuboidEntry result = new CuboidEntry(fun,-1,(new Path(cmd.getOptionValue("inp")).suffix(funString)));
	    		CuboidEntry desc = graphCube.getNearestDescendant(fun);
	    		cuboidArgs[0] = "-inp"; cuboidArgs[1] = desc.getPath().toString();
	    		System.out.println("Calculating from : " + desc.getAggregateFunction().toString() );
	    		cuboidArgs[8] = "-f"; cuboidArgs[9] = funString;
	    		cuboidArgs[10] = "-oup"; cuboidArgs[11] = result.getPath().toString();
	    		
	    		try{
	    	    	CuboidProcessor.main(cuboidArgs);
	    	    }
	    	    catch(Exception ex){
	    	    	System.err.println(ex.getMessage());
	    	    	System.exit(-1);
	    	    }
	    		
	    		long size = -1;
	    		try{
	    			File sizeFile = new File("tempSize.txt");
	    			BufferedReader reader = new BufferedReader(new FileReader(sizeFile));
	    			size = Long.parseLong(reader.readLine());
	    			reader.close();
	    			sizeFile.delete();
	    			
	    		}
	    		catch(IOException ex){
	    			System.out.println("Error when reading cuboid size :" + ex.getMessage());
	    		}
	    		System.out.println("size : " + size);
	    		
	    		result.setSize(size);
	    		graphCube.addCuboid(result);
	    		
	    	}
	    	System.out.println("finished, next input");
	    }
	    
	 
	}
}
