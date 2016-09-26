Name: 		yamcs
Version: 	$VERSION$+r$REVISION$
Release: 	10

Group:		MCS
Summary: 	Mission Control System

Vendor:		Space Applications Services
Packager:	Space Applications Services
License: 	AGPL (server) + LGPL (API)
URL: 		http://www.spaceapplications.com/
Source: 	%{name}-%{version}.tar.gz
BuildRoot: 	%{_tmppath}/%{name}-%{version}-buildroot
Prefix: 	/opt/yamcs
BuildArch:	noarch
Requires:       lsb
%description
Yet another Mission Control System

%prep
%setup 

%build

%if %{_buildweb}
  cd yamcs-web && npm install && gulp && cd ..
%endif

mvn clean compile package -Dmaven.test.skip=true -Dmaven.buildNumber.doUpdate=false

%install
mkdir -p %{buildroot}/%{prefix}/mdb
mkdir -p %{buildroot}/%{prefix}/log
mkdir -p %{buildroot}/%{prefix}/cache
mkdir -p %{buildroot}/etc # For system /etc
mkdir -p %{buildroot}/%{prefix}/lib/xtce
mkdir -p %{buildroot}/%{prefix}/lib/ext
mkdir -p %{buildroot}/%{prefix}/web/

cp -a yamcs-core/lib %{buildroot}/%{prefix}/
cp -a yamcs-core/etc %{buildroot}/%{prefix}/
cp -a yamcs-core/bin %{buildroot}/%{prefix}/
rm yamcs-core/target/yamcs-*-sources.jar
cp yamcs-core/target/yamcs*.jar %{buildroot}/%{prefix}/lib
cp -a yamcs-core/misc/init.d %{buildroot}/etc/
cp -a yamcs-api/src/main/*.proto %{buildroot}/%{prefix}/lib/

%if %{_buildweb}
cp -a yamcs-web/build/*  %{buildroot}/%{prefix}/web/
%endif

rm yamcs-simulation/target/yamcs-*-sources.jar
cp -a yamcs-simulation/target/*jar %{buildroot}/%{prefix}/lib/
cp -a yamcs-simulation/bin %{buildroot}/%{prefix}/


%clean
rm -rf %{buildroot}

%pre
if [ "$1" = 1 -o "$1" = install ] ; then
    groupadd -r yamcs >/dev/null 2>&1 || :
    useradd -M -r -d /opt/yamcs -g yamcs -s /bin/bash -c "Yamcs Suite" yamcs >/dev/null 2>&1 || :
fi

%postun
if [ "$1" = 0 -o "$1" = remove ] ; then
    userdel yamcs >/dev/null 2>&1 || :
fi

%files
%defattr(644,root,root,755)
%config %{prefix}/mdb
%config %{prefix}/etc
%config %{prefix}/bin
%{prefix}/lib
%{prefix}/web
%exclude %{prefix}/lib/ext
%exclude %{prefix}/lib/xtce


%defattr(644,yamcs,yamcs,755)
%{prefix}/cache
%{prefix}/log

%defattr(755,root,root,755) 
%config /etc/init.d/* 
%{prefix}/bin/*

%post

%changelog
* Tue Feb 23 2016 nm
- added an option to disable building yamcs-web part of the rpm
* Mon Nov 16 2015 fdi
- add build step for yamcs-web
* Wed Sep 24 2014 nm
- added css and html files for yamcs-web
* Fri Dec 14 2012 nm
- removed the yamcs-dass, yamcs-cdmcs, yamcs-busoc, yamcs-erasmus 
* Thu Apr 12 2012 nm
- added yamcs-web jar
* Thu Apr 12 2012 nm
- cmdhistory was wrongly placed in two packages
- added eutef js file to Erasmus RPM
* Fri Jul 8 2011 nm
- add the yamcs-cdmcs subpackage
* Thu Apr 28 2011 nm
- add the yamcs-dass subpackage
* Wed Apr 20 2011 nm
- add the yamcs-erasmus subpackage
* Mon Apr 11 2011 nm
- add the yamcs-busoc subpackage
* Thu Mar 31 2011 nm
- accomodate the yamcs-core yamcs-api split
* Thu Nov 20 2010 nm
- added yamcscontrol.idl in the lib directory
* Tue Nov 16 2010 nm
- fixed permissions 
* Mon May 17 2010 tn
- Updated build environment to maven
* Thu Mar 20 2008 nm
- Marked the init.d files as configuration files
* Fri Sep 07 2007 nm
- Changed ownership and access rights for a few configuration files containing passwords
* Fri Jul 26 2007 nm
- First version of the rpm
