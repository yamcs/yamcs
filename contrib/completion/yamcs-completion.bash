_yamcs ()
{
    local cur prev
    local subcommands="clients config instances links processors services storage tables"

    COMPREPLY=()
    _get_comp_words_by_ref cur prev

    if [[ $COMP_CWORD -eq 1 && $prev == "yamcs" ]]; then
        COMPREPLY=($(compgen -W "${subcommands}" -- ${cur}))
    fi
}
complete -o default -F _yamcs yamcs
