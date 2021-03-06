package hex.tree.gbm;

import hex.schemas.GBMModelV2;
import hex.tree.*;
import java.util.Arrays;
import water.Key;
import water.api.ModelSchema;
import water.fvec.Chunk;
import water.util.ArrayUtils;

public class GBMModel extends SharedTreeModel<GBMModel,GBMModel.GBMParameters,GBMModel.GBMOutput> {

  public static class GBMParameters extends SharedTreeModel.SharedTreeParameters {
    /** Distribution functions.  Note: AUTO will select gaussian for
     *  continuous, and multinomial for categorical response
     *
     *  <p>TODO: Replace with drop-down that displays different distributions
     *  depending on cont/cat response
     */
    public enum Family {  AUTO, bernoulli, multinomial, gaussian  }
    public Family _loss = Family.AUTO;
    public float _learn_rate=0.1f; // Learning rate from 0.0 to 1.0
  }

  public static class GBMOutput extends SharedTreeModel.SharedTreeOutput {
    public GBMOutput( GBM b, double mse_train, double mse_valid ) { super(b,mse_train,mse_valid); }
  }

  public GBMModel(Key selfKey, GBMParameters parms, GBMOutput output ) { super(selfKey,parms,output); }

  // Default publicly visible Schema is V2
  @Override public ModelSchema schema() { return new GBMModelV2(); }

  /** Bulk scoring API for one row.  Chunks are all compatible with the model,
   *  and expect the last Chunks are for the final distribution and prediction.
   *  Default method is to just load the data into the tmp array, then call
   *  subclass scoring logic. */
  @Override public float[] score0( Chunk chks[], int row_in_chunk, double[] tmp, float[] preds ) {
    assert chks.length>=tmp.length;
    for( int i=0; i<tmp.length; i++ )
      tmp[i] = chks[i].atd(row_in_chunk);
    return score0(tmp,preds);
  }

  @Override protected float[] score0(double data[/*ncols*/], float preds[/*nclasses+1*/]) {
    float[] p = super.score0(data, preds);    // These are f_k(x) in Algorithm 10.4
    if( _parms._loss == GBMParameters.Family.bernoulli ) {
      double fx = p[1] + _output._initialPrediction;
      p[2] = 1.0f/(float)(1f+Math.exp(-fx));
      p[1] = 1f-p[2];
      p[0] = water.util.ModelUtils.getPrediction(p, data);
      return p;
    }
    if( _output.nclasses()>1 ) { // classification
      if( _output.nclasses()==2 ) { // Kept the initial prediction for binomial
        p[1] += _output._initialPrediction;
        p[2] = - p[1];
      }
      // Because we call Math.exp, we have to be numerically stable or else
      // we get Infinities, and then shortly NaN's.  Rescale the data so the
      // largest value is +/-1 and the other values are smaller.
      // See notes here:  http://www.hongliangjie.com/2011/01/07/logsum/
      float maxval=Float.NEGATIVE_INFINITY;
      float dsum=0;
      // Find a max
      for( int k=1; k<p.length; k++) maxval = Math.max(maxval,p[k]);
      assert !Float.isInfinite(maxval) : "Something is wrong with GBM trees since returned prediction is " + Arrays.toString(p);
      for(int k=1; k<p.length;k++)
        dsum+=(p[k]=(float)Math.exp(p[k]-maxval));
      ArrayUtils.div(p,dsum);
      p[0] = water.util.ModelUtils.getPrediction(p, data);
    } else { // regression
      // Prediction starts from the mean response, and adds predicted residuals
      preds[0] += _output._initialPrediction;
    }
    return p;
  }

}
