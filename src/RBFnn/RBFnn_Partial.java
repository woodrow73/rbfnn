package RBFnn;
import java.util.Random;
import java.util.Vector;

import fileExport.AnglinFile;
import fileImport.DataImport;
import Clustering.centreWidths;
import Clustering.kMeans;

public class RBFnn_Partial 
{
	//Global Variables
	private int numPatterns, numinputs, numhidden, numoutputs, epochCounter=0;
	private double l_rate, RMSE, threshold;
	private Vector<DataImport.dataVals> training_Data;
	private Vector<DataImport.dataVals> validation_Data;
	Random rand;
	private boolean setSwitch = false;  //false=training set - true = validation set

	//global value arrays
	private double inputVals[][]; private double hiddenVals[][]; 
	private double outputVals[][]; private double targetVals[][]; 
	private double Error[][];
	
	//global node arrays
	private Input_neuron[] inputLayer;
	private Hidden_neuron[] hiddenLayer;
	private Output_neuron[] outputLayer;
	
	//constructor
	public RBFnn_Partial(String tset, String vset, int pat, int in, int hid, int out)
	{
		//assign global variables
		numPatterns = pat;
		numinputs = in;//inputs;
		numhidden = hid;//hidden;
		numoutputs = out;//outputs;
		rand = new Random();
		
		//create layers, spawn all neurons!!!
		inputLayer = new Input_neuron[numinputs];
		for (int i = 0; i<inputLayer.length; i++)
		{
			inputLayer[i] = new Input_neuron();
		}
		
		hiddenLayer = new Hidden_neuron[numhidden];
		for (int h = 0; h<hiddenLayer.length; h++)
		{
			hiddenLayer[h] = new Hidden_neuron();
		}
		
		outputLayer = new Output_neuron[numoutputs];
		for (int o = 0; o<outputLayer.length; o++)
		{
			outputLayer[o] = new Output_neuron();
		}
		//-end spawning-
		
		targetVals = new double[numPatterns][numoutputs];
		inputVals = new double [numPatterns][numinputs];
		hiddenVals = new double[numPatterns][numhidden];
		outputVals = new double[numPatterns][numoutputs];
		Error = new double[numPatterns][numoutputs];
		
		//import training and validation sets into network
		try 
		{
			DataImport importingTraining = new DataImport(tset);
			training_Data = importingTraining.sendData();
			DataImport importingValidation = new DataImport(vset);
			validation_Data = importingValidation.sendData();
		} catch (Exception e) 
		{
			e.printStackTrace();
		}
		
		//Calling Kmeans method here. Sending the whole set over. Will take up some memory
		//For demo purposes, it should be O.K.
		double[][] tempData = new double[training_Data.size()][2];
		
		//need to extract data into 2d array before passing
		for (int t=0; t<training_Data.size(); t++)
		{
			tempData[t][0] = training_Data.elementAt(t).getValue(0);
			tempData[t][1] = training_Data.elementAt(t).getValue(1);
		}
		
		double[][] newCentres = kMeans.computeClusters(tempData, numhidden);
		
		//assign new centres to hidden neurons
		for (int h = 0; h<numhidden; h++)
		{
			for (int i=0; i<numinputs; i++)
			{
				hiddenLayer[h].set1weight(i, newCentres[h][i]);
			}
		}
		
		//compute and assign new widths for centres
		double[] newWidths = centreWidths.computeWidths(newCentres);
		
		for (int h = 0; h<numhidden; h++)
		{
			for (int i=0; i<numinputs; i++)
			{
				hiddenLayer[h].set1Width(i, newWidths[h]);
			}
		}
		
		//set random linear weights here
		for (int o=0; o<numoutputs; o++)
		{
			for (int h=0; h<numhidden; h++)
			{
				outputLayer[o].set1linWeight(h, rand.nextDouble());
			}
		}
		
		runANDlearnNetwork ();
	} //end constructor
	
	/*********************************************************************************
	 * Function only runs the network to create the final output values.
	 * Useful when only the running of the network is required.
	 * Another function will be created to loop run and learn according to error size. 
	 *********************************************************************************/
	private void runNetwork()
	{
		Vector<DataImport.dataVals> Data = new Vector<DataImport.dataVals>();
		
		//choose to use either training set or validation set
		if (setSwitch)
		{
			Data = validation_Data;
		}
		else
		{
			Data = training_Data;
		}
		
		//send input patterns into input layer one at a time
		//pick 9 random training patterns for each epoch
		int maxRand = Data.size();
		
		for (int p=0; p<numPatterns; p++)
		{
			int nextPat = rand.nextInt(maxRand);
			
			inputVals[p][0] = Data.elementAt(nextPat).getValue(0);
			inputVals[p][1] = Data.elementAt(nextPat).getValue(2);
			targetVals[p][0] = Data.elementAt(nextPat).getValue(1);
			targetVals[p][1] = Data.elementAt(nextPat).getValue(3);
			
			inputLayer[0].setInputValue(inputVals[p][0]);
			inputLayer[1].setInputValue(inputVals[p][1]);
			
			for (int h = 0; h<numhidden; h++)
			{
				//compute RBF function and update array for hidden values for learning
				hiddenLayer[h].set_rbfValue();
				hiddenVals[p][h] = hiddenLayer[h].get_rbfValue();
			}
			
			//do for output layer
			for (int o=0; o<numoutputs; o++)
			{
				outputLayer[o].setOutputValue();
				double val = outputLayer[o].getOutputValue();
				outputVals[p][o]=val;
				Error[p][o]=targetVals[p][o]-outputVals[p][o];
			}
			
			//call learning method for training set only, send pattern index
			if (!setSwitch)
			{
				learn_partialSupervision(p);
			}
		}

		//calculating root-mean-squared error
		double sum =0;		
		for (int p=0; p<numPatterns; p++)
		{
			for (int o=0; o<numoutputs; o++)
			{
				//sum of all squared errors (I think)
				sum += Math.pow(Error[p][o], 2);
			}
		}
		
		RMSE = Math.sqrt(sum/(numPatterns*numoutputs));
	} //end runNetwork()
	
	/********************************************************************
	 * This function is for the full learning of the network calling the 
	 * network run and learning functions.
	 * 
	 * @author fq000476
	 ********************************************************************/
	private void runANDlearnNetwork()
	{
		/* number of epochs counted here
		 * In which we call runNetwork each epoch
		 * stopping condition when rmse rises/ epoch counter
		 * reaches maximum.
		 */
		for (int e=0; e<20000; e++)
		{
			//for every 100 epochs, use the validation set to test
			if (epochCounter%100==0)
			{
				setSwitch = true;
			}
			else
			{
				setSwitch = false;
			}
			runNetwork();
			
			if (!setSwitch)
			{
				RBFnn_GUI.addData(epochCounter, RMSE, "training");
			}
			else if (setSwitch)
			{
				RBFnn_GUI.addData(epochCounter, RMSE, "validation");
				RBFnn_GUI.consolePrint("Epoch "+epochCounter+": RMSE = " +RMSE+"\n");
				
				//Stopping conditions
				if (RMSE < threshold)
				{
					break;
				}
			}
			
			epochCounter++;
			
			//increase threshold when necessary
			if (epochCounter < 5000) {threshold = 0.125;}
			else if (epochCounter < 10000) {threshold = 0.15;}
			else {threshold = 0.175;}
			
		}
		
		saveNetworkConfig(); //save final network configuration to file
		
	}
	
	/********************************************************************
	 * Partial Supervision algorithm.
	 * This algorithm will only be used to update the linear weights
	 * of the output neuron connections.
	 * The KMeans algorithm updates the other parameters within the
	 * runNetwork() method.
	 * 
	 * @param patInd - Pattern Index
	 * 
	 * @author fq000476
	 *********************************************************************/
	private void learn_partialSupervision(int patInd)
	{
		l_rate = 0.3;
		//array of adjusted linear weights
		double nextLinweights[][] = new double[numoutputs][numhidden];
		
		/* -- Adjusting Linear Weights -- */
		
		//for each hidden node
		for (int j=0; j<numhidden; j++)
		{
			//for each outer node
			for (int k=0; k<numoutputs; k++)
			{
				double sum;
				sum = (Error[patInd][k]*hiddenVals[patInd][j]);
				double deltaW = sum*l_rate;
				nextLinweights[k][j] = outputLayer[k].get1linWeight(j)+deltaW;
			}
		}
		
		//Updating Linear Weights
		for (int k=0; k<nextLinweights.length; k++)
		{
			for (int h=0; h<nextLinweights[0].length; h++)
			{
				outputLayer[k].set1linWeight(h, nextLinweights[k][h]);
			}
		}
	}
	
	private void saveNetworkConfig()
	{
		int c=0, g=0;
		double[] centres = new double[numinputs*numhidden];
		double[] widths = new double[numinputs*numhidden];
		double[] Linweights = new double[numhidden*numoutputs];
		
		for (int j=0; j<numhidden; j++)
		{
			for (int i=0; i<numinputs; i++)
			{
				centres[c]=hiddenLayer[j].get1weight(i);
				widths[c]=hiddenLayer[j].get1Width(i);
				c++;		
			}
		}
		
		for (int k=0; k<numoutputs; k++)
		{
			for (int j=0; j<numhidden; j++)
			{
				Linweights[g]= outputLayer[k].get1linWeight(j);
				g++;
			}
		}
		
		AnglinFile.createFileSave
		(numinputs, numhidden, numoutputs, centres, widths, Linweights, "Network Configuration for Simulation Robots using Hybrid Supervision. By Daniel Anglin (FQ000476)");
		
		System.out.println("Network Configuration Saved!");
	}
	
	/******************************************************
	 *Input Neuron Class
	 *
	 *This class defines the properties of an input neuron
	 *
	 * @author fq000476
	 ******************************************************/
	class Input_neuron
	{
		//variables
		private double value;
		
		//methods
		void setInputValue(double val)
		{
			value = val;
		}
		
		double getInputValue()
		{
			return value;
		}
	}
	
	/******************************************************************
	 *Hidden Neuron Class 
	 *
	 *This class defines the properties of a radial basis function node
	 *as a hidden neuron/node
	 *
	 * @author fq000476
	 ******************************************************************/
	class Hidden_neuron
	{
		//variables
		int numInputs;
		double value;
		double weights[], widths[];
		
		//Hidden_neuron constructor
		Hidden_neuron()
		{
			numInputs = RBFnn_Partial.this.numinputs;
			weights = new double[numInputs];
			widths = new double[numInputs];
		}
		
		//methods
		void set_rbfValue()
		{
			double ProductSum = 1;
			for (int i=0; i<numInputs; i++)
			{
				double dist = Math.pow((inputLayer[i].getInputValue() - weights[i]), 2);
				double function = Math.exp(-(dist/widths[i]));
				ProductSum *=function;
			}		
			value = ProductSum;
		}
		
		double get_rbfValue()
		{
			return value;
		}
		
		//get and set a particular weight
		void set1weight(int index, double val)
		{
			weights[index] = val;
		}
		
		double get1weight(int index)
		{
			return weights[index];
		}
		
		//set and get all weights in array
		void setALLweights(double[] values)
		{
			//may want to set up error checking if necessary
			weights = values;
		}
		
		double[] getALLweights()
		{
			return weights;
		}
		
		//set and get neuron width
		void set1Width(int index, double value)
		{
			widths[index] = value;
		}
		
		double get1Width(int index)
		{
			return widths[index];
		}
		
		void setALLwidths(double[] values)
		{
			widths = values;
		}
		
		double[] getALLwidths()
		{
			return widths;
		}
		
	}
	
	/*******************************************************
	 *Outer Neuron Class
	 *
	 *This class defines the properties of an output neuron
	 *
	 * @author fq000476
	 *******************************************************/
	class Output_neuron
	{
		//variables
		int numHidden;
		double value;
		double[] linWeights;
		
		//Output neuron constructor
		Output_neuron()
		{
			numHidden = RBFnn_Partial.this.numhidden;
			linWeights = new double[numHidden];
		}
		
		//--*METHODS*--//
		
		//set and get output values
		void setOutputValue()
		{
			//linear activation
			double output = 0;
			
			for (int j=0; j<numHidden; j++)
			{
				//linear activation of output neuron
				output += hiddenLayer[j].get_rbfValue()*linWeights[j];
			}
			
			value = output;
		}
		
		double getOutputValue()
		{
			return value;
		}
		
		//set and get a particular Linear Weight value
		void set1linWeight(int index, double val)
		{
			linWeights[index] = val;
		}
		
		double get1linWeight(int index)
		{
			return linWeights[index];
		}
		
		//set and get all Linear weight values
		void setALLlinWeights(double[] values)
		{
			//may want to set up error checking if necessary
			linWeights = values;
		}
		
		double[] getALLlinWeights()
		{
			return linWeights;
		}
	}
}
