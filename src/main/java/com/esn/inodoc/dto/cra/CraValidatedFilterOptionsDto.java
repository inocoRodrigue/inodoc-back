package com.esn.inodoc.dto.cra;

import java.util.List;

public record CraValidatedFilterOptionsDto(List<Integer> years, List<CraCollaboratorDto> collaborators,
                                            List<String> validators, List<CraProjectOptionDto> projects) {
}