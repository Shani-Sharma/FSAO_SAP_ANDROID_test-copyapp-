package com.sap.copyapp.viewmodel.tofacetfilter;

import android.app.Application;
import android.os.Parcelable;

import com.sap.copyapp.viewmodel.EntityViewModel;
import com.sap.cloud.android.odata.eam_ntf_create_entities.TOFacetFilter;
import com.sap.cloud.android.odata.eam_ntf_create_entities.EAM_NTF_CREATE_EntitiesMetadata.EntitySets;

/*
 * Represents View model for TOFacetFilter
 * Having an entity view model for each <T> allows the ViewModelProvider to cache and
 * return the view model of that type. This is because the ViewModelStore of
 * ViewModelProvider cannot not be able to tell the difference between EntityViewModel<type1>
 * and EntityViewModel<type2>.
 */
public class TOFacetFilterViewModel extends EntityViewModel<TOFacetFilter> {

    /**
    * Default constructor for a specific view model.
    * @param application - parent application
    */
    public TOFacetFilterViewModel(Application application) {
        super(application, EntitySets.toFacetFilterSet, TOFacetFilter.listKey);
    }

    /**
    * Constructor for a specific view model with navigation data.
    * @param application - parent application
    * @param navigationPropertyName - name of the navigation property
    * @param entityData - parent entity (starting point of the navigation)
    */
	 public TOFacetFilterViewModel(Application application, String navigationPropertyName, Parcelable entityData) {
        super(application, EntitySets.toFacetFilterSet, TOFacetFilter.listKey, navigationPropertyName, entityData);
    }
}
