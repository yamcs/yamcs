Name: yamcs
Version: @@VERSION@@
Release: @@RELEASE@@

Group: MCS
Summary: Mission Control System

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: AGPL (server) + LGPL (API)
URL: https://www.yamcs.org
Prefix: /opt/yamcs
BuildArch: noarch


%description
Yet another Mission Control System


%install
cd %{name}-%{version}-%{release}

mkdir -p %{buildroot}
cp -r etc %{buildroot}
cp -r opt %{buildroot}

# Adjust the default location of the server logs
logproperties=%{buildroot}/opt/yamcs/etc/logging.properties.sample
sed -e 's/%h\/.yamcs\/log/\/opt\/yamcs\/log/g' $logproperties > $logproperties.tmp;
mv $logproperties.tmp $logproperties

rm %{buildroot}/%{prefix}/bin/*.bat


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

%attr(-,yamcs,yamcs) %{prefix}/cache
%attr(-,yamcs,yamcs) %{prefix}/log

%attr(755, root, root) /etc/init.d/*


%changelog
* Mon Jul 30 2018 fdi
- stripped out yamcs-client (now in distinct RPM)
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
