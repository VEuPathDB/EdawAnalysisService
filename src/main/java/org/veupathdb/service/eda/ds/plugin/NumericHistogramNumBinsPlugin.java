package org.veupathdb.service.eda.ds.plugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;
import org.gusdb.fgputil.IoUtil;
import org.rosuda.REngine.Rserve.RFileInputStream;
import org.veupathdb.service.eda.generated.model.NumericHistogramNumBinsPostRequest;
import org.veupathdb.service.eda.generated.model.NumericHistogramNumBinsSpec;

import static org.veupathdb.service.eda.ds.util.RServeClient.useRConnectionWithRemoteFiles;

public class NumericHistogramNumBinsPlugin extends HistogramPlugin<NumericHistogramNumBinsPostRequest, NumericHistogramNumBinsSpec>{

  @Override
  protected Class<NumericHistogramNumBinsSpec> getAnalysisSpecClass() {
    return NumericHistogramNumBinsSpec.class;
  }

  @Override
  protected void writeResults(OutputStream out, Map<String, InputStream> dataStreams) throws IOException {
    NumericHistogramNumBinsSpec spec = getPluginSpec();
/*
//  TODO revisit simpleHistogram for numBins rather than binWidth
    EntityDef entity = new EntityDef(spec.getEntityId());
    VariableDef xVar = entity.get(spec.getXAxisVariable());
    APIVariableType xType = xVar.getType();
    
	boolean simpleHistogram = false;
    if (spec.getOverlayVariable() == null 
    		&& spec.getFacetVariable() == null
    		&& spec.getNumBins() != null
    		&& xType.equals(APIVariableType.NUMBER)
    		&& spec.getValueSpec().equals('count')
    		&& dataStreams.size() == 1) {
      simpleHistogram = true;
    }

	if (simpleHistogram) {
	  Integer numBins = spec.getNumBins().intValue();
	  //TODO calculate binWidth from numBins and range
	  Wrapper<Integer> rowCount = new Wrapper<>(0);
	  Scanner s = new Scanner(dataStreams.get(0)).useDelimiter("\n");
	  s.nextLine(); // ignore header, expecting single column representing ordered xVar values
	  Double binStart = s.nextLine().asDouble();
	  rowCount.set(1);
	  Double nextBinStart = binStart + binWidth;
	  
	  while(s.hasNextLine()) {
            Double val = s.nextLine().asDouble();
            if (val >= nextBinStart) {
              JSONObject histogram = new JSONObject;
              histogram.put("binLabel", "[" + binStart + " - " + nextBinStart + ")");
              histogram.put("binStart", binStart);
              histogram.put("value", rowCount);
              out.write(histogram.toString());
              binStart = nextBinStart;
              nextBinStart = nextBinStart + binWidth;
              rowCount.set(1);
            } else {
              rowCount.set(rowCount.get() + 1);
            }    
	  }
	  
	  s.close();
	  out.flush();
    } else { 
*/  
      useRConnectionWithRemoteFiles(dataStreams, connection -> {
        connection.voidEval("data <- fread('" + DATAFILE_NAME + "', na.strings=c(''))");
        String facetVar1 = spec.getFacetVariable() != null ? toColNameOrEmpty(spec.getFacetVariable().get(0)) : "";
        String facetVar2 = spec.getFacetVariable() != null ? toColNameOrEmpty(spec.getFacetVariable().get(1)) : "";
        // NOTE: eventually varId and entityId will be a single string delimited by '.'
        String xAxisEntity = spec.getXAxisVariable() != null ? spec.getXAxisVariable().getEntityId() : "";
        String overlayEntity = spec.getXAxisVariable() != null ? spec.getXAxisVariable().getEntityId() : "";
        String facetEntity1 = spec.getXAxisVariable() != null ? spec.getXAxisVariable().getEntityId() : "";
        String facetEntity2 = spec.getXAxisVariable() != null ? spec.getXAxisVariable().getEntityId() : "";
        connection.voidEval("map <- data.frame("
            + "'plotRef'=c('xAxisVariable', "
            + "       'overlayVariable', "
            + "       'facetVariable1', "
            + "       'facetVariable2'), "
            + "'id'=c('" + toColNameOrEmpty(spec.getXAxisVariable()) + "'"
            + ", '" + toColNameOrEmpty(spec.getOverlayVariable()) + "'"
            + ", '" + facetVar1 + "'"
            + ", '" + facetVar2 + "'), "
            + "'entityId'=c('" + xAxisEntity + "'"
            + ", '" + overlayEntity + "'"
            + ", '" + facetEntity1 + "'"
            + ", '" + facetEntity2 + "'), stringsAsFactors=FALSE)");
        if (spec.getViewportMin() != null & spec.getViewportMax() != null) {
          connection.voidEval("viewport <- list('min'='" + spec.getViewportMin() + "', 'max'='" + spec.getViewportMax() + "')");
        } else {
          connection.voidEval("viewport <- NULL");
        }
        if (spec.getNumBins() != null) {
          String numBins = spec.getNumBins().toString();
          connection.voidEval("x <- emptyStringToNull(map$plotRef[map$id == 'xAxisVariable'])");
          connection.voidEval("xRange <- max(data[[x]], na.rm=T) - min(data[[x]], na.rm=T)");
          connection.voidEval("binWidth <- xRange*1.01/" + numBins);
        } else {
          connection.voidEval("binWidth <- NULL");
        }
        String outFile = connection.eval("histogram(data, map, binWidth, '" + spec.getValueSpec().toString().toLowerCase() + "', 'numBins', viewport)").asString();
        try (RFileInputStream response = connection.openFile(outFile)) {
          IoUtil.transferStream(out, response);
        }
        out.flush();
	   });
//  }
  }
}
