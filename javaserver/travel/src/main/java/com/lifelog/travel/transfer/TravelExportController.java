package com.lifelog.travel.transfer;

import com.lifelog.travel.train.TrainGeoPackageService;
import com.lifelog.travel.footprint.FootprintGeoPackageService;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Map;

/** 提供旅行数据的文件导出、模板下载和批量导入接口。 */
@RestController
@RequestMapping("/travel")
public class TravelExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final MediaType GPKG = MediaType.parseMediaType("application/geopackage+sqlite3");
    private static final MediaType CSV = MediaType.parseMediaType("text/csv;charset=UTF-8");
    private final TravelExportService exportService;
    private final TravelImportService importService;
    private final TrainGeoPackageService geoPackageService;
    private final FootprintGeoPackageService footprintGeoPackageService;

    /** 创建旅行数据传输控制器。 */
    public TravelExportController(TravelExportService exportService, TravelImportService importService,
                                  TrainGeoPackageService geoPackageService,
                                  FootprintGeoPackageService footprintGeoPackageService) {
        this.exportService = exportService;
        this.importService = importService;
        this.geoPackageService = geoPackageService;
        this.footprintGeoPackageService = footprintGeoPackageService;
    }

    /** 导出指定类型的页面数据。 */
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam String type,
                                         @RequestParam(required = false) String format) throws IOException {
        validateType(type);
        if (format != null && !format.equalsIgnoreCase("xlsx") && !format.equalsIgnoreCase("gpkg"))
            throw new IllegalArgumentException("导出格式仅支持 xlsx 或 gpkg");
        if ("train".equals(type)) {
            if ("xlsx".equalsIgnoreCase(format)) throw new IllegalArgumentException("铁路行程仅支持 GeoPackage");
            byte[] data = geoPackageService.exportAll();
            return attachment(data, "铁路行程-" + LocalDate.now() + ".gpkg", GPKG);
        }
        if ("gpkg".equalsIgnoreCase(format)) {
            if (!"footprint".equals(type)) throw new IllegalArgumentException("GeoPackage 导出仅支持足迹和铁路行程");
            byte[] data = footprintGeoPackageService.exportAll();
            return attachment(data, "旅行足迹-" + LocalDate.now() + ".gpkg", GPKG);
        }
        byte[] data = exportService.exportCategory(type);
        return attachment(data, ("flight".equals(type) ? "机票" : "足迹") + "-" + LocalDate.now() + ".xlsx", XLSX);
    }

    /** 下载指定旅行类型的导入模板。 */
    @GetMapping("/export/template")
    public ResponseEntity<byte[]> template(@RequestParam String type) throws IOException {
        validateType(type);
        if ("train".equals(type)) return attachment(geoPackageService.exportTemplate(), "铁路导入模板.gpkg", GPKG);
        if ("footprint".equals(type)) return attachment(footprintCsvTemplate(), "旅行足迹导入模板.csv", CSV);
        return attachment(exportService.exportTemplate(type), ("flight".equals(type) ? "机票" : "足迹") + "导入模板.xlsx", XLSX);
    }

    /** 根据旅行类型和导入模式处理上传文件。 */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> importFile(@RequestPart("file") MultipartFile file,
                                         @RequestParam String type,
                                         @RequestParam(defaultValue = "append") String mode) throws IOException {
        validateType(type);
        if (!mode.equals("append") && !mode.equals("update")) throw new IllegalArgumentException("导入模式仅支持 append 或 update");
        String filename = file.getOriginalFilename();
        if (filename == null) throw new IllegalArgumentException("上传文件名无效");
        if ("train".equals(type)) {
            if (!filename.toLowerCase().endsWith(".gpkg")) throw new IllegalArgumentException("铁路数据仅支持 GeoPackage（.gpkg）");
            return geoPackageService.importFile(file, mode.equals("update"));
        }
        if ("footprint".equals(type)) {
            if (!filename.toLowerCase().endsWith(".csv")) throw new IllegalArgumentException("足迹数据仅支持 CSV（.csv）");
            return importService.importFootprintsCsv(file, mode.equals("update"));
        }
        if (!filename.toLowerCase().endsWith(".xlsx")) throw new IllegalArgumentException("机票数据仅支持 Excel（.xlsx）");
        return importService.importWorkbook(file, mode.equals("update"), type);
    }

    private byte[] footprintCsvTemplate() {
        String header = "记录ID,地点名称,记录性质,到访日期,经度,纬度,行政区ID,地点类型,旅行心得,媒体URL列表JSON\r\n";
        byte[] content = header.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] withBom = new byte[content.length + 3];
        withBom[0] = (byte) 0xef;
        withBom[1] = (byte) 0xbb;
        withBom[2] = (byte) 0xbf;
        System.arraycopy(content, 0, withBom, 3, content.length);
        return withBom;
    }

    /** 校验导入或导出类型是否受支持。 */
    private void validateType(String type) {
        if (!"flight".equals(type) && !"train".equals(type) && !"footprint".equals(type))
            throw new IllegalArgumentException("type 仅支持 flight、train、footprint");
    }

    /** 创建带下载文件名和媒体类型的 HTTP 响应。 */
    private ResponseEntity<byte[]> attachment(byte[] data, String filename, MediaType mediaType) {
        ContentDisposition disposition = ContentDisposition.attachment().filename(filename, java.nio.charset.StandardCharsets.UTF_8).build();
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString()).contentType(mediaType).body(data);
    }
}
