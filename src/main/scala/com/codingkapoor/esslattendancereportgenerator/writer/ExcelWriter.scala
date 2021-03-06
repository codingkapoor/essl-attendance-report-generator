package com.codingkapoor.esslattendancereportgenerator.writer

import java.io.{File, FileInputStream, FileOutputStream}
import java.time.{LocalDate, YearMonth}
import com.typesafe.scalalogging.LazyLogging
import org.apache.poi.xssf.usermodel.XSSFWorkbook

import com.codingkapoor.esslattendancereportgenerator.AttendanceStatus.AttendanceStatus
import com.codingkapoor.esslattendancereportgenerator.`package`._
import com.codingkapoor.esslattendancereportgenerator.model._
import com.codingkapoor.esslattendancereportgenerator.writer.attendance.AttendanceWriter
import com.codingkapoor.esslattendancereportgenerator.writer.attendanceheader.AttendanceHeaderWriter
import com.codingkapoor.esslattendancereportgenerator.writer.companydetails.CompanyDetailsWriter
import com.codingkapoor.esslattendancereportgenerator.writer.employeeinfo.EmployeeInfoWriter
import com.codingkapoor.esslattendancereportgenerator.writer.employeeinfoheader.EmployeeInfoHeaderWriter
import com.codingkapoor.esslattendancereportgenerator.writer.holiday.HolidayWriter
import com.codingkapoor.esslattendancereportgenerator.writer.sheetheader.SheetHeaderWriter
import com.codingkapoor.esslattendancereportgenerator.writer.weekend.WeekendWriter

class ExcelWriter private(val month: Int, val year: Int, val attendances: Seq[Attendance], val employees: Seq[Employee], val holidays: Map[LocalDate, String])
  extends SheetHeaderWriter with CompanyDetailsWriter with EmployeeInfoHeaderWriter with EmployeeInfoWriter with AttendanceHeaderWriter with AttendanceWriter
    with HolidayWriter with WeekendWriter {
  val yearMonth: YearMonth = YearMonth.of(year, month)
  val monthTitle: String = yearMonth.getMonth.toString

  val attendanceDimensions: AttendanceDimensions = AttendanceDimensions(month, year, employees)
  logger.debug(s"attendanceDimensions = $attendanceDimensions")

  val attendanceHeaderDimensions: AttendanceHeaderDimensions = AttendanceHeaderDimensions(month, year)
  logger.debug(s"attendanceHeaderDimensions = $attendanceHeaderDimensions")

  val companyDetailsDimensions: CompanyDetailsDimensions = CompanyDetailsDimensions()
  logger.debug(s"companyDetailsDimensions = $companyDetailsDimensions")

  val employeeInfoDimensions: EmployeeInfoDimensions = EmployeeInfoDimensions(employees)
  logger.debug(s"employeeInfoDimensions = $employeeInfoDimensions")

  val employeeInfoHeaderDimensions: EmployeeInfoHeaderDimensions = EmployeeInfoHeaderDimensions()
  logger.debug(s"employeeInfoHeaderDimensions = $employeeInfoHeaderDimensions")

  val sectionHeaderDimensions: SectionHeaderDimensions = SectionHeaderDimensions(month, year)
  logger.debug(s"sectionHeaderDimensions = $sectionHeaderDimensions")

  override def mergedRegionAlreadyExists(firstRowIndex: Int, lastRowIndex: Int, firstColumnIndex: Int, lastColumnIndex: Int)(implicit workbook: XSSFWorkbook): Boolean = {
    val sheet = workbook.getSheet(monthTitle)

    var existingMergedRegionsFound = List.empty[Boolean]
    val mergedRegions = sheet.getMergedRegions.iterator()
    while (mergedRegions.hasNext) {
      val cellRangeAddr = mergedRegions.next()
      if (cellRangeAddr.getFirstRow == firstRowIndex && cellRangeAddr.getLastRow == lastRowIndex &&
        cellRangeAddr.getFirstColumn == firstColumnIndex && cellRangeAddr.getLastColumn == lastColumnIndex)
        existingMergedRegionsFound = true :: existingMergedRegionsFound
    }

    existingMergedRegionsFound.contains(true)
  }

  def write(): Unit = {
    val report = new File(AttendanceReportFileName)

    using {
      if (report.exists()) new XSSFWorkbook(new FileInputStream(report)) else new XSSFWorkbook
    } { implicit workbook =>
      workbook.createSheet(monthTitle)

      writeSheetHeader
      writeCompanyDetails
      writeEmployeeInfoHeader
      writeEmployeeInfo
      writeAttendanceHeader
      writeAttendance
      writeHolidays
      writeWeekends

      using(new FileOutputStream(AttendanceReportFileName))(workbook.write)

      logger.info(s"Essl attendance report written to $AttendanceReportFileName")
    }
  }
}

object ExcelWriter extends LazyLogging {
  def apply(month: Int, year: Int): ExcelWriter = {
    val holidays = Holiday.getHolidays(month, year)
    logger.debug(s"holidays = $holidays")

    val employees = Employee.getEmployees
    logger.debug(s"employees = $employees")

    val attendanceLogs = AttendanceLog.getAttendanceLogs(month, year)
    logger.debug(s"attendanceLogs = $attendanceLogs")

    val nonEmployeeEntries = attendanceLogs.keySet diff employees.map(e => e.empId).toSet
    logger.info(s"nonEmployeeEntries = $nonEmployeeEntries")

    val requests = Request.getRequests
    logger.debug(s"requests = $requests")

    val attendances: Seq[Attendance] = employees.map { employee =>
      val attendance = attendanceLogs.getOrElse(employee.empId, List.empty[LocalDate])
      val _requests = requests.getOrElse(employee.empId, Map.empty[LocalDate, AttendanceStatus])

      val att = Attendance.getAttendance(month, year, employee, attendance, holidays, _requests)
      logger.debug(s"emp = ${employee.empId}, att = ${att.attendance.toList.sortWith(_._1 < _._1)}")

      att
    }

    new ExcelWriter(month, year, attendances, employees, holidays)
  }
}
