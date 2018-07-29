Name: 		yamcs
Version: 	$VERSION$+r$REVISION$
Release: 	1

Group:		MCS
Summary: 	Mission Control System

Vendor:		Space Applications Services
Packager:	Yamcs Team <yamcs@spaceapplications.com>
License: 	AGPL (server) + LGPL (API)
URL: 		https://www.yamcs.org
Source: 	%{name}-%{version}.tar.gz
BuildRoot: 	%{_tmppath}/%{name}-%{version}-buildroot
Prefix: 	/opt/yamcs
BuildArch:	noarch

%description
Yet another Mission Control System

%clean
rm -rf %{buildroot}

%prep
%setup

%build

%if %{_buildweb}
    cd yamcs-web
    yarn install
    yarn build
    rm -rf `find . -name node_modules`
    cd ..
%endif

mvn clean compile package -Dmaven.test.skip=true -Dmaven.buildNumber.doUpdate=false

%install
mkdir -p %{buildroot}/%{prefix}/mdb
mkdir -p %{buildroot}/%{prefix}/log
mkdir -p %{buildroot}/%{prefix}/cache

cp -a yamcs-core/etc %{buildroot}/%{prefix}/
cp -an yamcs-client/etc %{buildroot}/%{prefix}/ || :

cp -a yamcs-server/bin %{buildroot}/%{prefix}/
cp -an yamcs-client/bin %{buildroot}/%{prefix}/ || :

cp -a yamcs-server/lib %{buildroot}/%{prefix}/
cp -an yamcs-client/lib %{buildroot}/%{prefix}/ || :
cp yamcs-client/target/yamcs*.jar %{buildroot}/%{prefix}/lib
cp yamcs-server/target/yamcs*.jar %{buildroot}/%{prefix}/lib
cp yamcs-artemis/lib/*.jar %{buildroot}/%{prefix}/lib
cp yamcs-artemis/target/yamcs-artemis*.jar %{buildroot}/%{prefix}/lib

# Placeholder for extensions
mkdir -p %{buildroot}/%{prefix}/lib/ext

mkdir -p %{buildroot}/etc/init.d
cp -a contrib/sysvinit/* %{buildroot}/etc/init.d/
cp -a yamcs-api/src/main/*.proto %{buildroot}/%{prefix}/lib/

%if %{_buildweb}
    mkdir -p %{buildroot}/%{prefix}/lib/yamcs-web
    cp -a yamcs-web/packages/app/dist/* %{buildroot}/%{prefix}/lib/yamcs-web/
%endif

# Clean-up
rm %{buildroot}/%{prefix}/bin/*.bat
rm %{buildroot}/%{prefix}/lib/yamcs-*-sources.jar

%pre
if [ "$1" = 1 -o "$1" = install ] ; then
    groupadd -r yamcs >/dev/null 2>&1 || :
    useradd -M -r -d %{prefix} -g yamcs -s /bin/bash -c "Yamcs daemon" yamcs >/dev/null 2>&1 || :
fi

%postun
if [ "$1" = 0 -o "$1" = remove ] ; then
    userdel yamcs >/dev/null 2>&1 || :
    groupdel yamcs >/dev/null 2>&1 || :
fi

%files
%defattr(-,root,root)

%dir %{prefix}
%config %{prefix}/mdb
%config %{prefix}/etc
%{prefix}/lib

%dir %{prefix}/bin
%attr(755, root, root) %{prefix}/bin/*

%attr(755, root, root) /etc/init.d/*

%attr(-,yamcs,yamcs) %{prefix}/cache
%attr(-,yamcs,yamcs) %{prefix}/log

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
* Sat Nov 20 2010 nm
- added yamcscontrol.idl in the lib directory
* Tue Nov 16 2010 nm
- fixed permissions 
* Mon May 17 2010 tn
- Updated build environment to maven
* Thu Mar 20 2008 nm
- Marked the init.d files as configuration files
* Fri Sep 07 2007 nm
- Changed ownership and access rights for a few configuration files containing passwords
* Thu Jul 26 2007 nm
- First version of the rpm
