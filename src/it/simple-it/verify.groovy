import java.nio.charset.StandardCharsets
import java.nio.file.Files

def xmlReport = new File(basedir, 'report.xml')
assert xmlReport.isFile()
String xmlReportStr = new String(Files.readAllBytes(xmlReport.toPath()), StandardCharsets.UTF_8)
assert xmlReportStr.contains('wk:///simple-it/simple.tst')

def htmlReport = new File(basedir, 'report.html')
assert htmlReport.isFile()
