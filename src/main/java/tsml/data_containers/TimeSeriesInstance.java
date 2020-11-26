package tsml.data_containers;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * Data structure able to store a time series instance. it can be standard
 * (univariate, no missing, equally sampled series) or complex (multivariate,
 * unequal length, unequally spaced, univariate or multivariate time series).
 *
 * Should Instances be immutable after creation? Meta data is calculated on
 * creation, mutability can break this
 */

public class TimeSeriesInstance extends AbstractList<TimeSeries> {

    /* Meta Information */
    private boolean isMultivariate;
    private boolean isEquallySpaced; // todo compute whether timestamps are equally spaced
    private boolean hasMissing;
    private boolean isEqualLength;

    private int minLength;
    private int maxLength;

    public boolean isMultivariate() {
        return isMultivariate;
    }

    public boolean isEquallySpaced() {
        return isEquallySpaced;
    }

    public boolean hasMissing() {
        return hasMissing;
    }

    /** 
     * @return boolean
     */
    public boolean isEqualLength(){
        return isEqualLength;
    }

    /** 
     * @return int
     */
    public int getMinLength() {
        return minLength;
    }

    
    /** 
     * @return int
     */
    public int getMaxLength() {
        return maxLength;
    }

    /* End Meta Information */


    /* Data */
    private List<TimeSeries> seriesDimensions;
    private int labelIndex = -1;
    private double targetValue = Double.NaN;
    private String[] classLabels = EMPTY_CLASS_LABELS;
    public static final String[] EMPTY_CLASS_LABELS = new String[0];

    /**
     * Construct a labelled instance from raw data.
     * @param series
     * @param labelIndex cast to an int internally
     * @param classLabels
     */
    public TimeSeriesInstance(List<? extends List<Double>> series, double labelIndex, String[] classLabels) {
        this(series, discretiseLabelIndex(labelIndex), classLabels);
    }

    /**
     * Construct a labelled instance from raw data.
     * @param series
     * @param label
     * @param classLabels
     */
    public TimeSeriesInstance(List<? extends List<Double>> series, int label, String[] classLabels) {
        this(series);

        targetValue = labelIndex = label;
        this.classLabels = classLabels;
        
        dataChecks();
    }

    /**
     * Construct an unlabelled / non-regressed instance from time series.
     * @param series
     */
    public TimeSeriesInstance(List<? extends List<Double>> series) {
        this(series, Double.NaN);
    }
    
    public TimeSeriesInstance(List<? extends List<Double>> series, double targetValue) {
        // process the input list to produce TimeSeries Objects.
        // this allows us to pad if need be, or if we want to squarify the data etc.
        seriesDimensions = new ArrayList<TimeSeries>();

        for (List<Double> ts : series) {
            // the list *could* be a TimeSeries already (as TimeSeries is a List<Double>). No need to copy if so.
            if(ts instanceof TimeSeries) {
                seriesDimensions.add((TimeSeries) ts);
            } else {
                seriesDimensions.add(new TimeSeries(ts));
            }
        }
        
        this.targetValue = targetValue;

        dataChecks();
    }

    /**
     * Construct an unlabelled / non-regressed instance from raw data.
     * @param data
     */
    public TimeSeriesInstance(double[][] data) {
        this(data, Double.NaN);
	}

    /**
     * Construct an regressed instance from raw data.
     * @param data
     * @param targetValue
     */
	public TimeSeriesInstance(double[][] data, double targetValue) {
        seriesDimensions = new ArrayList<TimeSeries>();

        for(double[] in : data){
            seriesDimensions.add(new TimeSeries(in));
        }
        
        this.targetValue = targetValue;

        dataChecks();
    }

    /**
     * Construct an labelled instance from raw data.
     * @param data
     * @param labelIndex
     * @param classLabels
     */
    public TimeSeriesInstance(double[][] data, int labelIndex, String[] classLabels) {
        seriesDimensions = new ArrayList<TimeSeries>();

        for(double[] in : data){
            seriesDimensions.add(new TimeSeries(in));
        }

        targetValue = this.labelIndex = labelIndex;
        this.classLabels = classLabels;
        
        dataChecks();
    }
    
    public static int discretiseLabelIndex(double labelIndex) {
        int i = (int) labelIndex;
        if(labelIndex != i) {
            throw new IllegalArgumentException("cannot discretise " + labelIndex + " to an int: " + i);
        }
        return i;
    }

    /**
     * Construct a labelled instance from raw data with label in double form (but should be an integer value).
     * @param data
     * @param labelIndex
     * @param classLabels
     */
    public TimeSeriesInstance(double[][] data, double labelIndex, String[] classLabels) {
        this(data, discretiseLabelIndex(labelIndex), classLabels);
    }

    /**
     * Construct an instance from raw data. Copies over regression target / labelling variables. This is only intended for internal use in avoiding copying the data again after a vslice / hslice.
     * @param data
     * @param other
     */
    private TimeSeriesInstance(double[][] data, TimeSeriesInstance other) {
        this(data);
        labelIndex = other.labelIndex;
        targetValue = other.targetValue;
        classLabels = other.classLabels;
        
        dataChecks();
    }
    
    public TimeSeriesInstance(TimeSeries[] data) {
        this(Arrays.asList(data));
    }
    
    public TimeSeriesInstance(TimeSeries[] data, double targetValue) {
        this(Arrays.asList(data), targetValue);
    }
    
    public TimeSeriesInstance(TimeSeries[] data, int labelIndex, String[] classLabels) {
        this(Arrays.asList(data), labelIndex, classLabels);
    }
    
    public TimeSeriesInstance(TimeSeries[] data, double labelIndex, String[] classLabels) {
        this(Arrays.asList(data), discretiseLabelIndex(labelIndex), classLabels);
    }

    private void dataChecks(){
        
        if(seriesDimensions == null) {
            throw new NullPointerException("no series dimensions");
        }
        // check class labels have been set correctly
        if(classLabels == null) {
            // class labels should always be set, even to an empty array if you're using regression instances
            throw new NullPointerException("no class labels");
        }
        // if there are no class labels
        if(Arrays.equals(classLabels, EMPTY_CLASS_LABELS)) {
            // then the class label index should be -1
            if(labelIndex != -1) {
                throw new IllegalStateException("no class labels but label index not -1: " + labelIndex);
            }
        } else {
            // there are class labels
            // therefore this is a classification instance, so the regression target should be the same as the class label index
            if(labelIndex != targetValue) {
                throw new IllegalStateException("label index (" + labelIndex + ") and target value (" + targetValue + ") mismatch");
            }
        }
        
        calculateIfMultivariate();
        calculateLengthBounds();
        calculateIfMissing();
    }
    
    private void calculateIfMultivariate(){
        isMultivariate = seriesDimensions.size() > 1;
    }

	private void calculateLengthBounds() {
        minLength = seriesDimensions.stream().mapToInt(TimeSeries::getSeriesLength).min().getAsInt();
        maxLength = seriesDimensions.stream().mapToInt(TimeSeries::getSeriesLength).max().getAsInt();
        isEqualLength = minLength == maxLength;
    }

    private void calculateIfMissing() {
        // if any of the series have a NaN value, across all dimensions then this is
        // true.
        hasMissing = seriesDimensions.stream().anyMatch(e -> e.streamValues().anyMatch(Double::isNaN));
    };

    
    /** 
     * @return int
     */
    public int getNumDimensions() {
        return seriesDimensions.size();
    }

    
    /** 
     * @return int
     */
    public int getLabelIndex(){
        return labelIndex;
    }
    
    public String getClassLabel() {
        if(labelIndex < 0 || classLabels == null) {
            return null;
        }
        return classLabels[labelIndex];
    }
    
    /** 
     * @param index
     * @return List<Double>
     */
    public List<Double> getVSliceList(int index){
        List<Double> out = new ArrayList<>(getNumDimensions());
        for(TimeSeries ts : seriesDimensions){
            out.add(ts.getValue(index));
        }

        return out;
    }
    
    /** 
     * @param index
     * @return double[]
     */
    public double[] getVSliceArray(int index){
        double[] out = new double[getNumDimensions()];
        int i=0;
        for(TimeSeries ts : seriesDimensions){
            out[i++] = ts.getValue(index);
        }

        return out;
    }
    
    /** 
     * @param indexesToKeep
     * @return List<List<Double>>
     */
    public List<List<Double>> getVSliceList(int[] indexesToKeep){
        return getVSliceList(Arrays.stream(indexesToKeep).boxed().collect(Collectors.toList()));
    }

    
    /** 
     * @param indexesToKeep
     * @return List<List<Double>>
     */
    public List<List<Double>> getVSliceList(List<Integer> indexesToKeep){
        List<List<Double>> out = new ArrayList<>(getNumDimensions());
        for(TimeSeries ts : seriesDimensions){
            out.add(ts.getVSliceList(indexesToKeep));
        }

        return out;
    }

 
    
    /** 
     * @param indexesToKeep
     * @return double[][]
     */
    public double[][] getVSliceArray(int[] indexesToKeep){
        return getVSliceArray(Arrays.stream(indexesToKeep).boxed().collect(Collectors.toList()));
    }

    
    /** 
     * @param indexesToKeep
     * @return double[][]
     */
    public double[][] getVSliceArray(List<Integer> indexesToKeep){
        double[][] out = new double[getNumDimensions()][];
        int i=0;
        for(TimeSeries ts : seriesDimensions){
            out[i++] = ts.getVSliceArray(indexesToKeep);
        }

        return out;
    }
    
    public TimeSeriesInstance getVSlice(List<Integer> indexesToKeep) {
        return new TimeSeriesInstance(getVSliceArray(indexesToKeep), this);
    }
    
    public TimeSeriesInstance getVSlice(int[] indexesToKeep) {
        return getVSlice(Arrays.stream(indexesToKeep).boxed().collect(Collectors.toList()));
    }
    
    public TimeSeriesInstance getVSlice(int index) {
        return getVSlice(new int[] {index});
    }


    
    /** 
     * @param dim
     * @return List<Double>
     */
    public List<Double> getHSliceList(int dim){
        return seriesDimensions.get(dim).getSeries();
    }

    
    /** 
     * @param dim
     * @return double[]
     */
    public double[] getHSliceArray(int dim){
        return seriesDimensions.get(dim).toValueArray();
    }

    
    /** 
     * @param dimensionsToKeep
     * @return List<List<Double>>
     */
    public List<List<Double>> getHSliceList(int[] dimensionsToKeep){
        return getHSliceList(Arrays.stream(dimensionsToKeep).boxed().collect(Collectors.toList()));
    }

    
    /** 
     * TODO: not a clone. may need to be careful...
     * @param dimensionsToKeep
     * @return List<List<Double>>
     */
    public List<List<Double>> getHSliceList(List<Integer> dimensionsToKeep){
        List<List<Double>> out = new ArrayList<>(dimensionsToKeep.size());
        for(Integer dim : dimensionsToKeep)
            out.add(seriesDimensions.get(dim).getSeries());

        return out;
    }

    
    /** 
     * @param dimensionsToKeep
     * @return double[][]
     */
    public double[][] getHSliceArray(int[] dimensionsToKeep){
        return getHSliceArray(Arrays.stream(dimensionsToKeep).boxed().collect(Collectors.toList()));
    }

    
    /** 
     * @param dimensionsToKeep
     * @return double[][]
     */
    public double[][] getHSliceArray(List<Integer> dimensionsToKeep){
        double[][] out = new double[dimensionsToKeep.size()][];
        int i=0;
        for(Integer dim : dimensionsToKeep){
            out[i++] = seriesDimensions.get(dim).toValueArray();
        }

        return out;
    }
    
    public TimeSeriesInstance getHSlice(List<Integer> dimensionsToKeep) {
        return new TimeSeriesInstance(getHSliceArray(dimensionsToKeep), this);
    }
    
    public TimeSeriesInstance getHSlice(int[] dimensionsToKeep) {
        return getHSlice(Arrays.stream(dimensionsToKeep).boxed().collect(Collectors.toList()));
    }
    
    public TimeSeriesInstance getHSlice(int index) {
        return getHSlice(new int[] {index});
    }

    
    /** 
     * @return String
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();

        sb.append("Num Dimensions: ").append(getNumDimensions()).append(" Class Label Index: ").append(labelIndex);
        for (TimeSeries ts : seriesDimensions) {
            sb.append(System.lineSeparator());
            sb.append(ts.toString());
        }

        return sb.toString();
    }
    
    /** 
     * @return double[][]
     */
    public double[][] toValueArray(){
        double[][] output = new double[this.seriesDimensions.size()][];
        for (int i=0; i<output.length; ++i){
             //clone the data so the underlying representation can't be modified
            output[i] = seriesDimensions.get(i).toValueArray();
        }
        return output;
    }

    
    /** 
     * @return double[][]
     */
    public double[][] toTransposedArray(){
        return this.getVSliceArray(IntStream.range(0, maxLength).toArray());
    }
	
    /** 
     * @param i
     * @return TimeSeries
     */
    public TimeSeries get(int i) {
        return this.seriesDimensions.get(i);
	}
	

    public double getTargetValue() {
        return targetValue;
    }

    @Override public int size() {
        return getNumDimensions();
    }

    @Override public void add(final int i, final TimeSeries doubles) {
        throw new UnsupportedOperationException("TimeSeriesInstance not mutable");
    }

    @Override public TimeSeries set(final int i, final TimeSeries doubles) {
        throw new UnsupportedOperationException("TimeSeriesInstance not mutable");
    }

    @Override public void clear() {
        throw new UnsupportedOperationException("TimeSeriesInstance not mutable");
    }

    @Override public TimeSeries remove(final int i) {
        throw new UnsupportedOperationException("TimeSeriesInstance not mutable");
    }

    public String[] getClassLabels() {
        return classLabels;
    }
}
