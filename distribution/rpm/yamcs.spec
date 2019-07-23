Name: yamcs
Version: @@VERSION@@
Release: @@RELEASE@@

Group: MCS
Summary: Mission Control System

Vendor: Space Applications Services
Packager: Yamcs Team <yamcs@spaceapplications.com>
License: Affero GPL v3
URL: https://www.yamcs.org
Prefix: /opt/yamcs
BuildArch: noarch


%description
Yamcs Mission Control


%install
cd %{name}-%{version}-%{release}

mkdir -p %{buildroot}
cp -r etc %{buildroot}
cp -r opt %{buildroot}
cp -r usr %{buildroot}


%pre
if [ "$1" = 1 -o "$1" = install ] ; then
    groupadd -r yamcs >/dev/null 2>&1 || :
    useradd -M -r -d %{prefix} -g yamcs -s /bin/false -c "Yamcs daemon" yamcs >/dev/null 2>&1 || :
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
/usr/lib/systemd/system/*

%dir %{prefix}/bin
%attr(755, root, root) %{prefix}/bin/*

%attr(-,yamcs,yamcs) %{prefix}/cache
%attr(-,yamcs,yamcs) %{prefix}/log

%attr(755, root, root) /etc/init.d/*
