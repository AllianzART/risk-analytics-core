package org.pillarone.riskanalytics.core.report;

import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.engine.export.ooxml.JRPptxExporter;
import net.sf.jasperreports.engine.export.ooxml.JRXlsxExporter;
import net.sf.jasperreports.engine.util.JRLoader;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.export.JRXlsExporter;


import java.io.ByteArrayOutputStream;

public abstract class ReportFactory {

    private interface JRExporterCreator {
        JRExporter createExporter();
    }

    public enum ReportFormat {

        PDF(new JRExporterCreator() {
            @Override
            public JRExporter createExporter() {
                return new JRPdfExporter();
            }
        }, "PDF", "pdf"),
        PPT(new JRExporterCreator() {
            @Override
            public JRExporter createExporter() {
                return new JRPptxExporter();
            }
        }, "PowerPoint", "pptx"),
        XLSX(new JRExporterCreator() {
            @Override
            public JRExporter createExporter() {
                return new JRXlsxExporter();
            }
        }, "Excel 2010", "xlsx"),
        XLS(new JRExporterCreator() {
            @Override
            public JRExporter createExporter() {
                return new JRXlsExporter();
            }
        }, "Excel", "xls");

        private final JRExporterCreator jrExporter;
        private final String renderedFormatSuchAsPDF;
        private final String fileExtension;

        private ReportFormat(JRExporterCreator jrExporter, String renderedFormatSuchAsPDF, String fileExtension) {
            this.jrExporter = jrExporter;
            this.renderedFormatSuchAsPDF = renderedFormatSuchAsPDF;
            this.fileExtension = fileExtension;
        }

        public JRExporter getExporter() {
            return jrExporter.createExporter();
        }

        public String getRenderedFormatSuchAsPDF() {
            return renderedFormatSuchAsPDF;
        }

        public String getFileExtension() {
            return fileExtension;
        }
    }

    public static byte[] createPDFReport(IReportModel reportModel, IReportData reportData) {
        return createReport(reportModel, reportData, new JRPdfExporter());
    }

    public static byte[] createPPTXReport(IReportModel reportModel, IReportData reportData) {
        return createReport(reportModel, reportData, new JRPptxExporter());
    }

    public static byte[] createXLSReport(IReportModel reportModel, IReportData reportData) {
        return createReport(reportModel, reportData, new JRXlsExporter());
    }

    public static byte[] createReport(IReportModel reportModel, IReportData reportData, ReportFormat format) {
        return createReport(reportModel, reportData, format.getExporter());
    }

    public static byte[] createReport(IReportModel reportModel, IReportData reportData, JRExporter exporter) {
        ByteArrayOutputStream reportOutputStream = new ByteArrayOutputStream();
        exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, reportOutputStream);

        try {
            JasperReport jasperReport = (JasperReport) JRLoader.loadObject(reportModel.getMainReportFile());
            jasperReport.setWhenNoDataType(jasperReport.WHEN_NO_DATA_TYPE_ALL_SECTIONS_NO_DETAIL);
            java.util.Map parameters = reportModel.getParameters(reportData);
            JRDataSource dataSource = reportModel.getDataSource(reportData);
            JasperPrint jasperPrint = JasperFillManager.fillReport(jasperReport, parameters, dataSource);

            exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
            exporter.exportReport();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create Jasper report: " +
                    e.getMessage()
                    + "\nreportModel: " + reportModel
                    + "\nreportData: " + reportData, e);
        }

        return reportOutputStream.toByteArray();

    }

}
