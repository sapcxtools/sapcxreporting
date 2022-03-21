package tools.sapcx.commerce.reporting.backoffice.action;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Resource;

import com.hybris.cockpitng.actions.ActionContext;
import com.hybris.cockpitng.actions.ActionResult;
import com.hybris.cockpitng.actions.CockpitAction;

import org.apache.commons.io.FilenameUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zkoss.zhtml.Filedownload;
import org.zkoss.zhtml.Messagebox;

import tools.sapcx.commerce.reporting.model.QueryReportConfigurationModel;
import tools.sapcx.commerce.reporting.report.ReportService;
import tools.sapcx.commerce.reporting.search.GenericSearchResult;
import tools.sapcx.commerce.reporting.search.GenericSearchService;

public class ExecuteReportAction implements CockpitAction<QueryReportConfigurationModel, Object> {
	private static final Logger LOG = LoggerFactory.getLogger(ExecuteReportAction.class);
	private static final String CONFIRMATION = "executereport.confirmation";
	private static final String SEARCH_ERROR = "executereport.errors.search";
	private static final String REPORT_GENERATE_ERROR = "executereport.errors.generation";
	private static final String FILE_READ_ERROR = "executereport.errors.fileread";

	@Resource
	private GenericSearchService genericFlexibleSearch;

	@Resource
	private ReportService dataReportService;

	@Override
	public ActionResult<Object> perform(ActionContext<QueryReportConfigurationModel> actionContext) {
		QueryReportConfigurationModel report = actionContext.getData();
		String query = report.getSearchQuery();
		Map<String, Object> params = dataReportService.getReportParameters(report);

		LOG.debug("Executing query {} with params {}", query, params);
		GenericSearchResult searchResult = genericFlexibleSearch.search(query, params);

		if (searchResult.hasError()) {
			return error(MessageFormat.format(actionContext.getLabel(SEARCH_ERROR), searchResult.getError()));
		}

		Optional<File> reportFile = dataReportService.getReportFile(report, searchResult);
		if (!reportFile.isPresent()) {
			return error(actionContext.getLabel(REPORT_GENERATE_ERROR));
		}

		File media = reportFile.get();
		try {
			String extension = FilenameUtils.getExtension(media.getAbsolutePath());
			Filedownload.save(new FileInputStream(media), Files.probeContentType(media.toPath()), report.getTitle() + "." + extension);
			return success();
		} catch (IOException e) {
			LOG.error("Error reading media file for report " + report.getTitle(), e);
			return error(actionContext.getLabel(FILE_READ_ERROR));
		} finally {
			media.delete();
		}
	}

	private ActionResult<Object> success() {
		return new ActionResult<>(ActionResult.SUCCESS);
	}

	private ActionResult<Object> error(String msg) {
		Messagebox.show(msg, "Error", Messagebox.OK, Messagebox.ERROR);

		ActionResult<Object> result = new ActionResult<>(ActionResult.ERROR);
		result.setResultMessage(msg);
		return result;
	}

	@Override
	public boolean needsConfirmation(ActionContext<QueryReportConfigurationModel> ctx) {
		return true;
	}

	@Override
	public String getConfirmationMessage(ActionContext<QueryReportConfigurationModel> ctx) {
		return ctx.getLabel(CONFIRMATION);
	}
}
