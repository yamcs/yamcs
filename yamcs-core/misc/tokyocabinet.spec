Name: tokyocabinet
Summary: A modern implementation of DBM
Version: 1.4.46
Release: 1
Vendor: Mikio Hirabayashi
License: GNU LGPL
Group: Applications/Database
Source: http://fallabs.com/tokyocabinet/tokyocabinet-1.4.46.tar.gz
Requires: zlib
BuildRoot:  %{_tmppath}/%{name}-%{version}-buildroot

%define tcdocdir %{_docdir}/%{name}-%{version}
%define tcsharedir /usr/share/tokyocabinet

%description
Tokyo Cabinet is a library of routines for managing a database. The database is a simple data file containing records, each is a pair of a key and a value. Every key and value is serial bytes with variable length. Both binary data and character string can be used as a key and a value.

Tokyo Cabinet is developed as the successor of GDBM and QDBM.
* improves space efficiency : smaller size of database file.
* improves time efficiency : faster processing speed.
* improves parallelism : higher performance in multi-thread environment.
* improves usability : simplified API.
* improves robustness : database file is not corrupted even under catastrophic situation.
* supports 64-bit architecture : enormous memory space and database file are available.

%package devel
Summary: Development files for Tokyo Cabinet
Group: Applications/Database

%description devel
Development support files for Tokyo Cabinet DBM

%prep
%setup -q
#%patch0 -p1
%pre

%build
./configure \
  --prefix=%{_prefix} \
  --sysconfdir=%{_sysconfdir} \
  --libdir=%{_libdir} \
  --libexecdir=%{_libexecdir}/%{name} \
  --docdir=%{tcdocdir} \
  --enable-fastest \
  --with-bzip=%{_prefix} \
  --with-zlib=%{_prefix}

%{__make}

%install
%{__mkdir_p} %{buildroot}/%{_libexecdir}/%{name}
%{__make} install DESTDIR=%{buildroot}
%{__mkdir_p} %{buildroot}/%{_docdir}
%{__mv} %{buildroot}/usr/share/tokyocabinet/doc/ %{buildroot}/%{tcdocdir}
%{__mv} %{buildroot}/%{tcsharedir}/COPYING %{buildroot}/%{tcdocdir}
%{__mv} %{buildroot}/%{tcsharedir}/ChangeLog %{buildroot}/%{tcdocdir}

%clean
%{__rm} -rf %{buildroot}

%files
%{_bindir}/tcamgr
%{_bindir}/tcamttest
%{_bindir}/tcatest
%{_bindir}/tcbmgr
%{_bindir}/tcbmttest
%{_bindir}/tcbtest
%{_bindir}/tcfmgr
%{_bindir}/tcfmttest
%{_bindir}/tcftest
%{_bindir}/tchmgr
%{_bindir}/tchmttest
%{_bindir}/tchtest
%{_bindir}/tctmgr
%{_bindir}/tctmttest
%{_bindir}/tcttest
%{_bindir}/tcucodec
%{_bindir}/tcumttest
%{_bindir}/tcutest
%{_libdir}/libtokyocabinet.so
%{_libdir}/libtokyocabinet.so.9
%{_libdir}/libtokyocabinet.so.9.9.0
%dir %{_libexecdir}/%{name}
%{_libexecdir}/%{name}/tcawmgr.cgi
%{_mandir}/man1/tcamgr.1.gz
%{_mandir}/man1/tcamttest.1.gz
%{_mandir}/man1/tcatest.1.gz
%{_mandir}/man1/tcbmgr.1.gz
%{_mandir}/man1/tcbmttest.1.gz
%{_mandir}/man1/tcbtest.1.gz
%{_mandir}/man1/tcfmgr.1.gz
%{_mandir}/man1/tcfmttest.1.gz
%{_mandir}/man1/tcftest.1.gz
%{_mandir}/man1/tchmgr.1.gz
%{_mandir}/man1/tchmttest.1.gz
%{_mandir}/man1/tchtest.1.gz
%{_mandir}/man1/tctmgr.1.gz
%{_mandir}/man1/tctmttest.1.gz
%{_mandir}/man1/tcttest.1.gz
%{_mandir}/man1/tcucodec.1.gz
%{_mandir}/man1/tcumttest.1.gz
%{_mandir}/man1/tcutest.1.gz
%dir %{tcdocdir}
%{tcdocdir}/COPYING
%{tcdocdir}/ChangeLog
%{tcdocdir}/benchmark.pdf
%{tcdocdir}/common.css
%{tcdocdir}/icon16.png
%{tcdocdir}/index.html
%{tcdocdir}/index.ja.html
%{tcdocdir}/logo-ja.png
%{tcdocdir}/logo.png
%{tcdocdir}/spex-en.html
%{tcdocdir}/spex-ja.html
%{tcdocdir}/tokyoproducts.pdf
%{tcdocdir}/tokyoproducts.ppt

%dir %{tcsharedir}
%{tcsharedir}/tokyocabinet.idl

%files devel
%{_includedir}/tcadb.h
%{_includedir}/tcbdb.h
%{_includedir}/tcfdb.h
%{_includedir}/tchdb.h
%{_includedir}/tctdb.h
%{_includedir}/tcutil.h
%{_libdir}/libtokyocabinet.a
%{_libdir}/pkgconfig/tokyocabinet.pc
%{_mandir}/man3/tcadb.3.gz
%{_mandir}/man3/tcbdb.3.gz
%{_mandir}/man3/tcfdb.3.gz
%{_mandir}/man3/tchdb.3.gz
%{_mandir}/man3/tclist.3.gz
%{_mandir}/man3/tcmap.3.gz
%{_mandir}/man3/tcmdb.3.gz
%{_mandir}/man3/tcmpool.3.gz
%{_mandir}/man3/tctdb.3.gz
%{_mandir}/man3/tctree.3.gz
%{_mandir}/man3/tcutil.3.gz
%{_mandir}/man3/tcxstr.3.gz
%{_mandir}/man3/tokyocabinet.3.gz

