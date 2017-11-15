_yamcs ()
{
    local cur prev
    local subcommands="archive backup confcheck parchive tables rocksdb xtcedb"

    COMPREPLY=()
    _get_comp_words_by_ref cur prev

    if [[ $COMP_CWORD -eq 1 && $prev == "yamcs" ]]; then
        COMPREPLY=($(compgen -W "${subcommands}" -- ${cur}))
    fi
}
complete -o default -F _yamcs yamcs
