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
 * {@link AnalysisSampleUpdater} for ECTYPER results to be written to
 * metadata of {@link Sample}s.
 */
public class ECTyperUpdater implements AnalysisSampleUpdater {
	private static final Logger logger = LoggerFactory.getLogger(ECTyperUpdater.class);
	/*private static final String STARAMR_SUMMARY = "staramr-summary.tsv";*/
	private static final String ECTYPER_SUMMARY = "serotyping report"; /*got to match the value in the irida_workflow.xml file <output name="serotyping report" fileName="report_ectyper.tsv"/>*/
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
	 * Gets the ectyper results from the given output.tsv file.
	 * 
	 * @param ectyperreportFilePath The ectyper output file (report_ectyper.txt) containing the results.
	 * @return A {@link ECTYPERResult} storing the results from ectyper as
	 *         {@link String}s.
	 * @throws IOException             If there was an issue reading the file.
	 * @throws PostProcessingException If there was an issue parsing the file.
	*/
	private ECTYPERResult getECTYPERResults(Path ectyperreportFilePath) throws IOException, PostProcessingException { 
		final int SPECIES_INDEX = 1;
		final int OTYPE_INDEX = 2;
		final int HTYPE_INDEX = 3;
		final int SEROTYPE_INDEX = 4;
		final int QCFLAG_INDEX = 5;
		final int EVIDENCE_INDEX = 6;
		final int GENESCORES_INDEX = 7;
		final int IDENTITIES_INDEX = 9;
		final int COVERAGES_INDEX = 10;
		final int DATABASE_VER_INDEX = 14;
		final int WARNINGS_INDEX = 15;
		final int MAX_TOKENS = 12;

		@SuppressWarnings("resource")
		BufferedReader reader = new BufferedReader(new FileReader(ectyperreportFilePath.toFile()));
		String line = reader.readLine();
		List<String> tokens = SPLITTER.splitToList(line);
		

		line = reader.readLine();
		tokens = SPLITTER.splitToList(line);
		String species = tokens.get(SPECIES_INDEX);
		String otype = tokens.get(OTYPE_INDEX);
		String htype = tokens.get(HTYPE_INDEX);
		String serotype = tokens.get(SEROTYPE_INDEX);
		String qcflag = tokens.get(QCFLAG_INDEX);
		String evidence = tokens.get(EVIDENCE_INDEX);
		String genescores = tokens.get(GENESCORES_INDEX);
		String identities = tokens.get(IDENTITIES_INDEX);
		String coverages = tokens.get(COVERAGES_INDEX);
		String dbver = tokens.get(DATABASE_VER_INDEX);
		String warnings = tokens.get(WARNINGS_INDEX);

		line = reader.readLine();

		if (line == null) {
			return new ECTYPERResult(species, otype, htype, serotype, qcflag, evidence, genescores, 
				identities, coverages, dbver, warnings);
		} else {
			throw new PostProcessingException("Invalid ectyper results file [" + ectyperreportFilePath + "], expected only single line of results but got empty file.");
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


		Map<String, MetadataEntry> metadataEntries = new HashMap<>();
		try {
			IridaWorkflow iridaWorkflow = iridaWorkflowsService.getIridaWorkflow(analysis.getWorkflowId());
			String workflowVersion = iridaWorkflow.getWorkflowDescription().getVersion();

			ECTYPERResult ectyperResult = getECTYPERResults(ectyperreportFilePath);
			
			PipelineProvidedMetadataEntry ectyperSpeciesEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getSpecies(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperOtypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getOtype(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperHtypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getHtype(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperSerotypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getSerotype(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperQCflagEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getQCflag(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperEvidenceTypeEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getEvidenceType(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperGeneScores = new PipelineProvidedMetadataEntry(
					ectyperResult.getGeneScores(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperIdentities = new PipelineProvidedMetadataEntry(
					ectyperResult.getIdentities(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperCoverages = new PipelineProvidedMetadataEntry(
					ectyperResult.getCoverages(), "text", analysis);
			PipelineProvidedMetadataEntry ectyperDatabase = new PipelineProvidedMetadataEntry(
					ectyperResult.getDatabaseVersion(), "text", analysis);

			PipelineProvidedMetadataEntry ectyperWarningsEntry = new PipelineProvidedMetadataEntry(
					ectyperResult.getWarnings(), "text", analysis);

			/*Write results to the metadata of project*/
			metadataEntries.put(appendVersion("ECTYPER-1-Species", workflowVersion), ectyperSpeciesEntry);
			metadataEntries.put(appendVersion("ECTYPER-2-O-antigen", workflowVersion), ectyperOtypeEntry);
			metadataEntries.put(appendVersion("ECTYPER-3-H-antigen", workflowVersion), ectyperHtypeEntry);
			metadataEntries.put(appendVersion("ECTYPER-4-Serotype",  workflowVersion), ectyperSerotypeEntry);
			metadataEntries.put(appendVersion("ECTYPER-5-QCFlag",    workflowVersion), ectyperQCflagEntry);
			metadataEntries.put(appendVersion("ECTYPER-6-Evidence Type",  workflowVersion), ectyperEvidenceTypeEntry);
			metadataEntries.put(appendVersion("ECTYPER-7-GeneScores",  workflowVersion), ectyperGeneScores);
			metadataEntries.put(appendVersion("ECTYPER-8-Identities",  workflowVersion), ectyperIdentities);
			metadataEntries.put(appendVersion("ECTYPER-9-Coverages",  workflowVersion), ectyperCoverages);
			metadataEntries.put(appendVersion("ECTYPER-10-Database",  workflowVersion), ectyperDatabase);
			metadataEntries.put(appendVersion("ECTYPER-11-Warnings",  workflowVersion), ectyperWarningsEntry);

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
	 * Class used to store together data extracted from the ectyper tab-result files.
	 */
	private class ECTYPERResult  {
		private String species;
		private String otype;
		private String htype;
		private String serotype;
		private String qcflag;
		private String evidence;
		private String genescores;
		private String identities;
		private String coverages;
		private String dbver;
		private String warnings;

		public ECTYPERResult(String species, String otype, String htype, String serotype, 
			String qcflag,  String evidence, String genescores, String identities, String coverages,
			String dbver, String warnings) {
			this.species = species;
			this.otype = otype;
			this.htype = htype;
			this.serotype = serotype;
			this.qcflag = qcflag;
			this.evidence = evidence;
			this.genescores = genescores;
			this.identities = identities;
			this.coverages = coverages;
			this.dbver = dbver;
			this.warnings = warnings;
		}

		public String getSpecies() {
			return species;
		}
		public String getOtype() {
			return otype;
		}
		public String getHtype() {
			return htype;
		}
		public String getSerotype(){
			return serotype;
		}
		public String getQCflag(){
		    return qcflag;
		}
		public String getEvidenceType(){
		    return evidence;
		}
		public String getGeneScores(){
		    return genescores;
		}
		public String getIdentities(){
		    return identities;
		}
		public String getCoverages(){
		    return coverages;
		}
		public String getDatabaseVersion(){
		    return dbver;
		}
		public String getWarnings(){
		    return warnings;
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