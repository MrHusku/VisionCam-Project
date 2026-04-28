package org.mrhusku.service;
import org.mrhusku.model.Furniture;
import org.mrhusku.repository.FurnitureRepository;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.print.attribute.standard.Media;
import java.util.List;
import java.util.Map;

@Service
public class FurnitureService {
    @Autowired
    private FurnitureRepository furnitureRepository;
    @Autowired
    private RestTemplate restTemplate;
    // adresa pt python
    private final String AI_URL = "http://127.0.0.1:8000/analyze";


    public Furniture processAndSave(MultipartFile file)
    {
        try{
            // pregatire fisiser pt HTTP
            HttpHeaders headers= new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);

            MultiValueMap<String,Object> body = new LinkedMultiValueMap<>();

            HttpEntity<MultiValueMap<String,Object>> requestEntity = new HttpEntity<>(body,headers);
            body.add("file",file.getResource());

            // trimitere cerere catre python
            ResponseEntity<Map> response = restTemplate.postForEntity(AI_URL,requestEntity,Map.class);
            Map<String,Object> result= response.getBody();

            // extragere date de la AI
            Furniture furniture = new Furniture();
            furniture.setName((String) result.get("detected_item"));

            //valori intregi

            furniture.setWidth((Integer) result.get("width"));
            furniture.setHeight((Integer) result.get("height"));

            return furnitureRepository.save(furniture);
        } catch (Exception e)
        {   e.printStackTrace();
            throw new RuntimeException("Eroare laa comunicare cu serveru Ai");
        }
    }
    public List<Furniture> getAllFurniture() {
        return furnitureRepository.findAll();
    }
    public Furniture saveFurniture(Furniture furniture){
         return furnitureRepository.save(furniture);
    }
}
