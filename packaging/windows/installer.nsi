Unicode true
!include "MUI2.nsh"
!include "x64.nsh"
!include "LogicLib.nsh"
Name "ZenCode 测试版"
OutFile "${OUTPUT}"
InstallDir "$LOCALAPPDATA\ZenCode\${RELEASE}"
RequestExecutionLevel user
SetCompressor /SOLID lzma
SetCompressorDictSize 32
BrandingText "ZenCode · 离线代码编辑器"
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
VIProductVersion "0.9.0.0"
VIAddVersionKey /LANG=2052 "ProductName" "ZenCode"
VIAddVersionKey /LANG=2052 "FileDescription" "ZenCode 测试版安装程序"
VIAddVersionKey /LANG=2052 "FileVersion" "${RELEASE}"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TEXT "安装 ZenCode 测试版及配套 JDK 8。支持离线安装。请先关闭正在运行的 ZenCode。$\r$\n$\r$\n快捷方式使用“文档”目录中的 ZenCodeWorkspace。卸载保留项目文件。"
!insertmacro MUI_PAGE_WELCOME
!define MUI_DIRECTORYPAGE_TEXT_TOP "请选择一个空目录。为避免覆盖已有程序或项目，不支持安装到非空目录。"
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "SimpChinese"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "此安装包需要 64 位 Windows。"
    Abort
  ${EndIf}
  SetShellVarContext current
FunctionEnd

; Require an empty destination so existing installations and user files are never overwritten.
Function .onVerifyInstDir
  IfFileExists "$INSTDIR\*.*" 0 done
  FindFirst $0 $1 "$INSTDIR\*.*"
  loop:
    StrCmp $1 "" empty
    StrCmp $1 "." next
    StrCmp $1 ".." next
    FindClose $0
    Abort
  next:
    FindNext $0 $1
    Goto loop
  empty:
    FindClose $0
  done:
FunctionEnd

Section "ZenCode"
  SetOutPath "$INSTDIR"
  File /r "${PAYLOAD}/*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateDirectory "$SMPROGRAMS\ZenCode ${RELEASE}"
  CreateShortcut "$SMPROGRAMS\ZenCode ${RELEASE}\ZenCode.lnk" "$INSTDIR\ZenCode.exe" '-vm "$INSTDIR\jdk8\bin\javaw.exe" -data "$DOCUMENTS\ZenCodeWorkspace"' "$INSTDIR\ZenCode.exe" 0
  CreateShortcut "$SMPROGRAMS\ZenCode ${RELEASE}\卸载.lnk" "$INSTDIR\Uninstall.exe"
  CreateShortcut "$DESKTOP\ZenCode ${RELEASE}.lnk" "$INSTDIR\ZenCode.exe" '-vm "$INSTDIR\jdk8\bin\javaw.exe" -data "$DOCUMENTS\ZenCodeWorkspace"' "$INSTDIR\ZenCode.exe" 0
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "DisplayName" "ZenCode ${RELEASE} (含 JDK 8)"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "DisplayVersion" "${RELEASE}"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "UninstallString" '$\"$INSTDIR\Uninstall.exe$\"'
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "DisplayIcon" "$INSTDIR\ZenCode.exe"
  WriteRegStr HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "InstallLocation" "$INSTDIR"
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "NoModify" 1
  WriteRegDWORD HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}" "NoRepair" 1
SectionEnd

Section "Uninstall"
  SetShellVarContext current
  !include "${DELETE_MANIFEST}"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  Delete "$DESKTOP\ZenCode ${RELEASE}.lnk"
  Delete "$SMPROGRAMS\ZenCode ${RELEASE}\ZenCode.lnk"
  Delete "$SMPROGRAMS\ZenCode ${RELEASE}\卸载.lnk"
  RMDir "$SMPROGRAMS\ZenCode ${RELEASE}"
  DeleteRegKey HKCU "Software\Microsoft\Windows\CurrentVersion\Uninstall\ZenCode-${RELEASE}"
SectionEnd
