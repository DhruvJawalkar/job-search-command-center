package dev.dhruv.jobsearch;

import java.nio.file.*;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/** Opt-in synthetic workbook export for browser acceptance; never reads private workbooks. */
@EnabledIfSystemProperty(named="c5.fixtures",matches="true")
class C5FixtureExportTest {
 @Test void export() throws Exception {
  Path root=Path.of(System.getProperty("c5.root")).toAbsolutePath().normalize();
  if(!root.toString().contains("c5-") || !Files.exists(root.resolve("instance.json"))) throw new IllegalArgumentException("Use an initialized C5 workspace.");
  int rows=Integer.getInteger("c5.rows",24);
  if(rows<1 || rows>1000) throw new IllegalArgumentException("C5 fixtures are bounded to 1–1000 rows.");
  for(int day=0;day<2;day++)try(var book=new XSSFWorkbook()){
   var sheet=book.createSheet("High-Fit Openings");
   var headings=List.of("Rank","Company","Exact Title","Location / Work Arrangement","Posting Date","Overall Fit","Recruiter-Screen Strength","Technical Scope","Growth Potential","Weighted Total","Recommendation","Role Summary","Fit Rationale","Key Risks / Gaps","Direct Job Link","Recommended Resume Variant","Authorization / Eligibility","Verified Date");
   var header=sheet.createRow(0); for(int c=0;c<headings.size();c++)header.createCell(c).setCellValue(headings.get(c));
   for(int i=0;i<rows;i++){
    var r=sheet.createRow(i+1);r.createCell(0).setCellValue(i+1);r.createCell(1).setCellValue("C5 Fixture "+String.format("%02d",i));r.createCell(2).setCellValue("Senior Backend Engineer");r.createCell(3).setCellValue("Bengaluru / Hybrid");
    for(int c=5;c<=9;c++)r.createCell(c).setCellValue(9);
    r.createCell(10).setCellValue("Apply");r.createCell(11).setCellValue("Workbook summary: Java, Kubernetes. Not full-description skill evidence.");r.createCell(12).setCellValue("Synthetic acceptance fixture");r.createCell(14).setCellValue("https://example.invalid/c5/"+i);r.createCell(15).setCellValue("C5 Synthetic Resume");
   }
   try(var out=Files.newOutputStream(root.resolve("daily-high-fit-job-roles").resolve(LocalDate.now().minusDays(day)+"-high-fit-openings.xlsx"))){book.write(out);}
  }
 }
}
