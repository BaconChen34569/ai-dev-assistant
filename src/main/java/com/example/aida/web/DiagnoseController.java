package com.example.aida.web;

import com.example.aida.agent.DiagnosisAgent;
import com.example.aida.model.DiagnosticRequest;
import com.example.aida.model.DiagnosticResponse;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@CrossOrigin
public class DiagnoseController {
    private final DiagnosisAgent agent;

    public DiagnoseController(DiagnosisAgent agent) {
        this.agent = agent;
    }

    @PostMapping("/diagnose")
    public DiagnosticResponse diagnose(@RequestBody DiagnosticRequest req) {
        return agent.diagnose(req.getTrace());
    }
}
