Name: tokyocabinet-java
Summary: Java bindings for TokyoCabinet DBM
Version: 1.24
Release: 1
Vendor: Mikio Hirabayashi
License: GNU LGPL
Group: Applications/Database
Source: http://fallabs.com/tokyocabinet/javapkg/tokyocabinet-java-1.24.tar.gz
BuildRoot:  %{_tmppath}/%{name}-%{version}-buildroot

%define tcdocdir %{_docdir}/%{name}-%{version}
%define tcsharedir /usr/share/tokyocabinet

%description
Tokyo Cabinet is a library of routines for managing a database. The database is a simple data file containing records, each is a pair of a key and a value. Every key and value is serial bytes with variable length. Both binary data and character string can be used as a key and a value.
This package contains the java bindings.


%prep
%setup -q
%pre

%build
JAVA_HOME=/usr/java/default \
./configure \
  --prefix=%{_prefix} \
  --sysconfdir=%{_sysconfdir} \
  --libdir=%{_libdir} \
  --libexecdir=%{_libexecdir}/%{name} \
  --docdir=%{tcdocdir}


%{__make}

%install
%{__make} install DESTDIR=%{buildroot}

%clean
%{__rm} -rf %{buildroot}

%files
%{_libdir}/libjtokyocabinet.so
%{_libdir}/libjtokyocabinet.so.1
%{_libdir}/libjtokyocabinet.so.1.1.0
%{_libdir}/tokyocabinet.jar
