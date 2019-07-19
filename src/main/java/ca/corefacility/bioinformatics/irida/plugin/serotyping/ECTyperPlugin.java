package ca.corefacility.bioinformatics.irida.plugin.serotyping;

import java.awt.Color;
import java.util.Optional;
import java.util.UUID;

import org.pf4j.Extension;
import org.pf4j.Plugin;
import org.pf4j.PluginException;
import org.pf4j.PluginWrapper;

import ca.corefacility.bioinformatics.irida.model.workflow.analysis.type.AnalysisType;
import ca.corefacility.bioinformatics.irida.pipeline.results.updater.AnalysisSampleUpdater;
import ca.corefacility.bioinformatics.irida.plugins.IridaPlugin;
import ca.corefacility.bioinformatics.irida.plugins.IridaPluginException;
import ca.corefacility.bioinformatics.irida.service.sample.MetadataTemplateService;
import ca.corefacility.bioinformatics.irida.service.sample.SampleService;
import ca.corefacility.bioinformatics.irida.service.workflow.IridaWorkflowsService;


public class ECTyperPlugin extends Plugin {

	public static AnalysisType ECTYPER = new AnalysisType("ECTYPER");

	public ECTyperPlugin(PluginWrapper wrapper) {
		super(wrapper);
	}

	@Override
	public void start() throws PluginException {
	}

	@Extension
	public static class PluginInfo implements IridaPlugin {

		@Override
		public Optional<AnalysisSampleUpdater> getUpdater(MetadataTemplateService metadataTemplateService,
				SampleService sampleService, IridaWorkflowsService iridaWorkflowsService) throws IridaPluginException {
			return Optional.empty();
		}

		@Override
		public AnalysisType getAnalysisType() {
			return ECTYPER;
		}

		@Override
		public UUID getDefaultWorkflowUUID() {
			return UUID.fromString("c9f4a5e8-4419-4f2e-8d39-e446bcd967af");
		}

		@Override
		public Optional<Color> getBackgroundColor() {
			return Optional.of(Color.decode("#66c2a4"));
		}
	}
}
