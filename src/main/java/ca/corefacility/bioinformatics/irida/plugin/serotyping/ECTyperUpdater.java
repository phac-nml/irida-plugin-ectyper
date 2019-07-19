package ca.corefacility.bioinformatics.irida.plugin.serotyping;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

import ca.corefacility.bioinformatics.irida.exceptions.IridaWorkflowNotFoundException;
import ca.corefacility.bioinformatics.irida.exceptions.PostProcessingException;
import ca.corefacility.bioinformatics.irida.model.sample.MetadataTemplateField;
import ca.corefacility.bioinformatics.irida.model.sample.Sample;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.MetadataEntry;
import ca.corefacility.bioinformatics.irida.model.sample.metadata.PipelineProvidedMetadataEntry;
import ca.corefacility.bioinformatics.irida.model.workflow.IridaWorkflow;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.AnalysisOutputFile;
import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.model.workflow.submission.AnalysisSubmission;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;

/**
 * {@link AnalysisSampleUpdater} for AMR detection results to be written to
 * metadata of {@link Sample}s.
 */
public class ECTyperUpdater implements AnalysisSampleUpdater {
	private static final Logger logger = LoggerFactory.getLogger(ECTyperUpdater.class);
	/*private static final String STARAMR_SUMMARY = "staramr-summary.tsv";*/
	private static final String ECTYPER_SUMMARY = "report_ectyper.tsv";
	private static final Splitter SPLITTER = Splitter.on('\t');
	

	private MetadataTemplateService metadataTemplateService;
	private SampleService sampleService;
	private IridaWorkflowsService iridaWorkflowsService;

	/**
	 * Builds a new {@link ecTyperUpdater} with the following information.
	 * 
	 * @param metadataTemplateService The {@link MetadatTemplateService}.
	 * @param sampleService           The {@link SampleService}.
	 * @param iridaWorkflowsService   The {@link IridaWorkflowsService}.
	 */
	public ECTyperUpdater(MetadataTemplateService metadataTemplateService, SampleService sampleService,
			IridaWorkflowsService iridaWorkflowsService) {
		this.metadataTemplateService = metadataTemplateService;
		this.sampleService = sampleService;
		this.iridaWorkflowsService = iridaWorkflowsService;
	}

	/**
	 * Gets the staramr results from the given output file.
	 * 
	 * @param ectyperreportFilePath The staramr output file containing the results.
	 * @return A {@link ECTYPERResult} storing the results from ectyper as
	 *         {@link String}s.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	 *private AMRResult getECTYPERResults(Path staramrFilePath) throws IOException, PostProcessingException { 
	*/
	private ECTYPERResult getECTYPERResults(Path ectyperreportFilePath) throws IOException, PostProcessingException { 
		final int OTYPE_INDEX = 2;
		final int HTYPE_INDEX = 3;
		/*final int DRUG_INDEX = 2;*/
		final int MAX_TOKENS = 10;

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(ectyperreportFilePath.toFile()));
		String line = reader.readLine();
		List<String> tokens = SPLITTER.splitToList(line);
		

		line = reader.readLine();
		tokens = SPLITTER.splitToList(line);
		String otype = tokens.get(OTYPE_INDEX);
		String htype = tokens.get(HTYPE_INDEX);

		line = reader.readLine();

		if (line == null) {
			return new ECTYPERResult(otype, htype);
		} else {
			throw new PostProcessingException("Invalid ectyper results file [" + ectyperreportFilePath + "], expected only single line of results but got multiple lines");
		}
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public void update(Collection<Sample> samples, AnalysisSubmission analysis) throws PostProcessingException {
		if (samples.size() != 1) {
			throw new PostProcessingException(
					"Expected one sample; got '" + samples.size() + "' for analysis [id=" + analysis.getId() + "]");
		}

		final Sample sample = samples.iterator().next();

		AnalysisOutputFile ectyperAof = analysis.getAnalysis().getAnalysisOutputFile(ECTYPER_SUMMARY);
		Path ectyperreportFilePath = ectyperAof.getFile();


		Map<String, MetadataEntry> stringEntries = new HashMap<>();
		try {
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();

			ECTYPERResult ectyperResult = getECTYPERResults(ectyperreportFilePath);
			

			PipelineProvidedMetadataEntry ectyperOtypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getOtype(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperHtypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getHtype(), "text", analysis);
			

			stringEntries.put(appendVersion("O-type", workflowVersion), ectyperOtypeEntry);
			stringEntries.put(appendVersion("H-type", workflowVersion), ectyperHtypeEntry);
			

			Map<MetadataTemplateField, MetadataEntry> metadataMap = metadataTemplateService
					.getMetadataMap(stringEntries);

			sample.mergeMetadata(metadataMap);
			sampleService.updateFields(sample.getId(), ImmutableMap.of("metadata", sample.getMetadata()));
		} catch (IOException e) {
			logger.error("Got IOException", e);
			throw new PostProcessingException("Error parsing amr detection results", e);
		} catch (IridaWorkflowNotFoundException e) {
			logger.error("Got IridaWorkflowNotFoundException", e);
			throw new PostProcessingException("Workflow is not found", e);
		} catch (Exception e) {
			logger.error("Got Exception", e);
			throw e;
		}
	}

	/**
	 * Appends the name and version together for a metadata field name.
	 * 
	 * @param name    The name.
	 * @param version The version.
	 * @return The appended name and version.
	 */
	private String appendVersion(String name, String version) {
		return name + "/" + version;
	}

	/**
	 * Class used to store together data extracted from the RGI/staramr tables.
	 */
	private class ECTYPERResult  {
		private String otype;
		private String htype;

		public ECTYPERResult(String otype, String htype) {
			this.otype = otype;
			this.htype = htype;
		}

		public String getOtype() {
			return otype;
		}

		public String getHtype() {
			return htype;
		}
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return ECTyperPlugin.ECTYPER;
	}
}