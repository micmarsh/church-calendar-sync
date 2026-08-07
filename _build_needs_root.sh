#!/bin/sh

# comment this out when testing (usually)
# may also want to just build manually?
lein jdeb

tmp_dir=DELETE_ME_temp_for_build
desktop_file_dir=${tmp_dir}/usr/share/applications
package_name=church-calendar-sync
deb_file=$(ls | grep $package_name | grep .deb)
desktop_file=${package_name}.desktop

dpkg-deb -R $deb_file $tmp_dir

mkdir -p $desktop_file_dir
cp $desktop_file  ${desktop_file_dir}/${desktop_file}

dpkg-deb -b $tmp_dir $deb_file

rm -r $tmp_dir