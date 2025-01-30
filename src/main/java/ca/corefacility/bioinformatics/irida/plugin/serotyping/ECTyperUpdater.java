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
import java.util.Scanner;
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
 * {@link AnalysisSampleUpdater} for ECTYPER results to be written to
 * metadata of {@link Sample}s.
 */
public class ECTyperUpdater implements AnalysisSampleUpdater {
	private static final Logger logger = LoggerFactory.getLogger(ECTyperUpdater.class);
	private static final String ECTYPER_SUMMARY = "report_ectyper.tab"; /*got to match the value in the irida_workflow.xml file <output name="report_ectyper.tab" fileName="report_ectyper.tab"/>*/
	private static final Splitter SPLITTER = Splitter.on('\t');
	

	private MetadataTemplateService metadataTemplateService;
	private SampleService sampleService;
	private IridaWorkflowsService iridaWorkflowsService;

	/* A map of ECTYPER report column names and corresponding IRIDA headers  */
	private Map<String, String> ECTYPER_REPORT_FIELDS = ImmutableMap.<String,String>builder()
		.put("Species", "ECTYPER-Species")
		.put("O-type","ECTYPER-O-antigen")
		.put("H-type", "ECTYPER-H-antigen")
		.put("Serotype", "ECTYPER-Serotype")
		.put("QC", "ECTYPER-QCFlag")
		.put("Evidence", "ECTYPER-EvidenceType")
		.put("GeneScores", "ECTYPER-GeneScores")
		.put("GeneIdentities(%)", "ECTYPER-Identities")
		.put("GeneCoverages(%)", "ECTYPER-Coverages")
		.put("DatabaseVer", "ECTYPER-Database")
		.put("Warnings", "ECTYPER-Warnings")
		.put("Pathotype", "ECTYPER-Pathotype")
		.put("PathotypeGenes", "ECTYPER-PathotypeGenes")
		.put("PathotypeGeneNames","ECTYPER-PathotypeGeneNames")
		.put("PathotypeAccessions", "ECTYPER-PathotypeGeneAccessions")
		.put("PathotypeIdentities(%)", "ECTYPER-PathotypeGeneIdentities(%)")
		.put("PathotypeCoverages(%)", "ECTYPER-PathotypeGeneCoverages(%)")
		.put("PathotypeGeneLengthRatios","ECTYPER-PathotypeGeneLengthRatios")
		.put("PathotypeRuleIDs","ECTYPER-PathotypeRuleIDs")
		.put("PathotypeGeneCounts","ECTYPER-PathotypeGeneCountsByPathotype")
		.put("PathoDBVer","ECTYPER-PathotypesDatabaseVersion")
		.put("StxSubtypes","ECTYPER-StxSubtypes")
		.put("StxAccessions","ECTYPER-StxGeneAccessions")
		.put("StxIdentities(%)","ECTYPER-StxIdentities(%)")
		.put("StxCoverages(%)", "ECTYPER-StxCoverages(%)")
		.put("StxLengths","ECTYPER-StxGeneLengths")
		.put("StxContigNames","ECTYPER-StxContigNames")
		.put("StxCoordinates","ECTYPER-StxGeneCoordinates")
		.build();

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
	 * Gets the ectyper results from the report_ectyper.tab (original output.tsv) file and generate {columnName}:{value} map/dictionary.
	 * 
	 * @param ectyperreportFilePath The ectyper output file (report_ectyper.tab) containing the results.
	 * @return A {@link ECTYPERResult} storing the results from ectyper as
	 *         {@link String}s.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	*/
	private Map<String, String> getECTYPERResults(Path ectyperreportFilePath) throws IOException, PostProcessingException { 
		Map<String, String> resultsMap = new HashMap<>();

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(ectyperreportFilePath.toFile()));
		String line = reader.readLine();
		List<String> columnNames = SPLITTER.splitToList(line);

		line = reader.readLine();
		List<String> values = SPLITTER.splitToList(line);

		if (columnNames.size() != values.size()) {
			throw new PostProcessingException("Mismatch in number of column names [" + columnNames.size()
					+ "] and number of fields/values [" + values.size() + "] in the results file [" + ectyperreportFilePath + "]");
		}

		for (int i = 0; i < columnNames.size(); i++) {
			String column = columnNames.get(i);
			String value = values.get(i);
			resultsMap.put(column, value);
		}

		return resultsMap;
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

		/* Read through ECTYPER_SUMMARY tab file and update meta data fields */
		Map<String, MetadataEntry> metadataEntries = new HashMap<>();
		try {
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();

			
			/* read results from the ECTYPER report file */
			System.out.println("Reading ECTYPER report file "+ectyperreportFilePath);
			Map<String, String> ectyperResultMap = getECTYPERResults(ectyperreportFilePath);
			
			for (String resultsFieldName : ECTYPER_REPORT_FIELDS.keySet()) {
				String value = ectyperResultMap.containsKey(resultsFieldName) ? ectyperResultMap.get(resultsFieldName) : "-";
				metadataEntries.put(appendVersion(resultsFieldName, workflowVersion), new PipelineProvidedMetadataEntry(value, "text", analysis));
			}
			
			/*Write results to the metadata of project*/
			Set<MetadataEntry> metadataSet = metadataTemplateService.convertMetadataStringsToSet(metadataEntries);
			sampleService.mergeSampleMetadata(sample,metadataSet);

		} catch (IOException e) {
			logger.error("Got IOException", e);
			throw new PostProcessingException("Error parsing ectyper results", e);
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
		return name + "(" + version + ")";
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public AnalysisType getAnalysisType() {
		return ECTyperPlugin.ECTYPER;
	}
}